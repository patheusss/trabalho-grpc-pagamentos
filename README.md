# Sistema de Pagamentos — Microsserviços gRPC (Java)

Trabalho da disciplina de **Sistemas Distribuídos**. Comunicação interna de
backend entre dois microsserviços via **gRPC / Protocol Buffers**, em **Java**,
com deploy em **infraestrutura de nuvem (Google Cloud Platform)**.

Domínio de negócio: uma **fintech de pagamentos** — um serviço solicita o
processamento de uma transação e o outro valida e processa contra os saldos
das contas.

---

## Arquitetura

```
              Rede VPC (GCP)
   ┌──────────────────────────────────────────────────────────┐
   │   vm-a-cliente  (Serviço A / Cliente gRPC)                 │
   │        │  chamada RPC → porta 50051 (HTTP/2 + protobuf)    │
   │        ▼                                                   │
   │   vm-b-servidor (Serviço B / Servidor gRPC · 0.0.0.0:50051)│
   └──────────────────────────────────────────────────────────┘
        ▲  Regra de firewall VPC: allow-grpc-50051
        └─ Ingress · Permitir · tcp:50051 · tag "grpc-server"
```

---

## Tecnologias

| Camada        | Tecnologia                              |
|---------------|-----------------------------------------|
| Protocolo RPC | gRPC (HTTP/2)                           |
| Serialização  | Protocol Buffers (proto3)              |
| Linguagem     | Java 17+                                |
| Build         | Maven (gera os stubs no `mvn compile`) |
| Nuvem         | Google Cloud Platform (Compute Engine, VPC) |

---

## Estrutura do projeto

```
fintech-grpc-java/
├── pom.xml                         # dependências + plugin que gera os stubs
└── src/main/
    ├── proto/
    │   └── pagamentos.proto         # o contrato (mensagens + serviço RPC)
    └── java/com/pagamentos/grpc/
        ├── PagamentoServer.java     # Serviço B — servidor gRPC
        └── PagamentoClient.java     # Serviço A — cliente gRPC
```

> As classes `PagamentoServiceGrpc`, `PagamentoRequest`, etc. são **geradas**
> pelo Maven a partir do `.proto` durante o `mvn compile` (vão para
> `target/generated-sources`) e não são versionadas.

---

## O contrato (`pagamentos.proto`)

Dois métodos RPC:

- `ProcessarPagamento(PagamentoRequest) → PagamentoResponse`
- `ConsultarSaldo(SaldoRequest) → SaldoResponse`

Tipos usados: `string`, `double` e um `enum Status`
(`APROVADO`, `SALDO_INSUFICIENTE`, `CONTA_INVALIDA`).

---

## Como executar

### Pré-requisitos

```bash
sudo apt-get update
sudo apt-get install -y default-jdk maven
```

### 1. Compilar (baixa deps + gera os stubs a partir do .proto)

```bash
mvn compile
```

### 2. Rodar o servidor (Serviço B)

```bash
mvn exec:java -Dexec.mainClass=com.pagamentos.grpc.PagamentoServer
```

### 3. Rodar o cliente (Serviço A), em outra máquina/terminal

```bash
# Local:
mvn exec:java -Dexec.mainClass=com.pagamentos.grpc.PagamentoClient

# Na GCP, apontando para o IP externo do servidor:
mvn exec:java -Dexec.mainClass=com.pagamentos.grpc.PagamentoClient \
    -Dexec.args="<IP-EXTERNO-DO-SERVIDOR>:50051"
```

---

## Infraestrutura (GCP)

1. Duas VMs (Compute Engine, e2-micro, Debian 12) na mesma zona, rede `default`.
2. A VM do servidor recebe a **tag de rede** `grpc-server`.
3. Regra de firewall VPC `allow-grpc-50051`: Ingress · Permitir ·
   tag de destino `grpc-server` · origem `0.0.0.0/0` · `tcp:50051`.

---

## Demonstração

O cliente executa três cenários: um pagamento **APROVADO** (com saldo
atualizado), um **SALDO_INSUFICIENTE** (rejeitado) e uma **consulta de saldo**.
Enquanto o cliente dispara as requisições em uma VM, o log do servidor na outra
VM registra cada uma sendo recebida e processada — demonstrando a troca de
mensagens serializadas entre os dois serviços através da rede.
