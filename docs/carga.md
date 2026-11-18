# Teste de carga

Script: [`load/transferencias.js`](../load/transferencias.js) (k6). Para rodar:

```bash
docker compose up -d --build
k6 run load/transferencias.js
```

Tudo roda na mesma máquina (Apple M5, 10 núcleos, 16 GB): k6, API, Postgres e RabbitMQ disputam a mesma CPU e o mesmo disco. Os números servem para comparar versões entre si, não como capacidade de produção. Cada linha é uma execução só.

## Cenário 1: conta disputada

200 usuários virtuais disparam, ao mesmo tempo, uma transferência de R$ 1,00 de uma conta com R$ 100,00.

| Esperado | Resultado |
|---|---|
| 100 aceitas (201) | 100 |
| 100 recusadas por saldo (422 `INSUFFICIENT_FUNDS`) | 100 |
| Saldo final da pagadora: R$ 0,00 | R$ 0,00 |
| Saldo final do recebedor: R$ 100,00 | R$ 100,00 |

Passou em todas as execuções. É o mesmo comportamento que os testes de integração já garantiam, agora pela API, com HTTP e JWT no caminho.

## Cenário 2: vazão

50 usuários virtuais transferem valores aleatórios entre 20 contas durante 30 segundos. O dinheiro só circula dentro do grupo, então a soma dos saldos tem que ser a mesma no começo e no fim.

Depois de cada execução, conferimos direto no banco:
- a soma de todos os saldos é zero (partidas dobradas);
- o saldo de cada conta é igual à soma dos lançamentos dela;
- nenhuma conta de cliente ficou negativa.

Os três passaram em todas as execuções.

## O que o teste de carga encontrou

O caminho do dinheiro aguentou desde a primeira execução. O que ficou para trás foi o envio dos eventos:

| Versão | Transferências em 30 s | p95 | Eventos presos no outbox no fim | Mensagens na fila de notificações no fim |
|---|---|---|---|---|
| 1. Confirmação do RabbitMQ a cada mensagem | 43.064 | 71 ms | **19.464** | - |
| 2. Confirmação por lote de 200 | 47.792 | 63 ms | 181 | **40.833** |
| 3. Lote + 4 consumidores de notificação | 39.998 | 76 ms | **0** | 26.153 |

1. **O publicador esperava o RabbitMQ confirmar cada mensagem antes de enviar a próxima.** A API produzia eventos mais rápido do que ele enviava. Agora ele manda o lote inteiro e espera uma confirmação só. A garantia é a mesma: se a confirmação falhar, nenhum evento do lote é marcado como enviado, e todos saem de novo depois.
2. **Com o outbox em dia, o gargalo passou para o consumidor de notificações**, que processava uma mensagem por vez. Como notificações não dependem de ordem e a deduplicação é feita por chave primária no banco, ele passou a rodar com 4 consumidores em paralelo. Um teste dispara 10 cópias do mesmo evento ao mesmo tempo e confere que sai uma notificação só.
3. **A fila ainda acumula durante o pico.** Neste notebook, API, consumidores e k6 disputam o mesmo Postgres e a mesma CPU, e a fila esvazia em uns 15 segundos depois que a carga para. Isso é consistência eventual, por escolha de projeto: a notificação pode atrasar, a transferência não. Na versão 3, os 40.119 eventos viraram exatamente 80.217 notificações (uma por depósito e duas por transferência), sem perda nem duplicata.

O resultado entre as versões 2 e 3 varia de uma execução para outra. Não dá para concluir, com uma execução de cada, que os consumidores em paralelo deixaram a API mais lenta.
