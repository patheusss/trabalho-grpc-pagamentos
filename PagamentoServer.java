package com.pagamentos.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SERVIÇO B — SERVIDOR gRPC (Sistema de Pagamentos)
 * Recebe requisições do Serviço A, processa contra os saldos
 * em memória e responde de forma síncrona.
 */
public class PagamentoServer {

    // "Banco de dados" em memória: conta -> saldo (equivale ao dict do Python)
    static final Map<String, Double> SALDOS = new HashMap<>();
    static {
        SALDOS.put("CONTA-100", 5000.00);
        SALDOS.put("CONTA-200", 1500.00);
        SALDOS.put("CONTA-300",    0.00); // conta sem saldo, para testar a rejeição
    }

    /**
     * A implementação do serviço herda a classe *ImplBase* GERADA a partir
     * do .proto e sobrescreve cada método RPC.
     */
    static class PagamentoServiceImpl extends PagamentoServiceGrpc.PagamentoServiceImplBase {

        @Override
        public void processarPagamento(PagamentoRequest request,
                                       StreamObserver<PagamentoResponse> responseObserver) {
            System.out.printf("[Servidor B] Requisição %s: %.2f %s | %s -> %s%n",
                    request.getIdTransacao(), request.getValor(), request.getMoeda(),
                    request.getContaOrigem(), request.getContaDestino());

            String agora = LocalDateTime.now().toString();
            PagamentoResponse.Builder resp = PagamentoResponse.newBuilder()
                    .setIdTransacao(request.getIdTransacao())
                    .setDataHora(agora);

            if (!SALDOS.containsKey(request.getContaOrigem())
                    || !SALDOS.containsKey(request.getContaDestino())) {
                // 1) Conta inexistente
                resp.setStatus(Status.CONTA_INVALIDA)
                    .setMensagem("Conta de origem ou destino não existe.")
                    .setSaldoRestante(SALDOS.getOrDefault(request.getContaOrigem(), 0.0));

            } else if (SALDOS.get(request.getContaOrigem()) < request.getValor()) {
                // 2) Saldo insuficiente
                resp.setStatus(Status.SALDO_INSUFICIENTE)
                    .setMensagem("Saldo insuficiente na conta de origem.")
                    .setSaldoRestante(SALDOS.get(request.getContaOrigem()));

            } else {
                // 3) Processa: debita a origem, credita o destino
                SALDOS.put(request.getContaOrigem(),
                           SALDOS.get(request.getContaOrigem()) - request.getValor());
                SALDOS.put(request.getContaDestino(),
                           SALDOS.get(request.getContaDestino()) + request.getValor());
                resp.setStatus(Status.APROVADO)
                    .setMensagem("Pagamento aprovado e processado com sucesso.")
                    .setSaldoRestante(SALDOS.get(request.getContaOrigem()));
            }

            // No Java, "responder" são dois passos (substituem o return do Python):
            responseObserver.onNext(resp.build()); // envia a resposta serializada
            responseObserver.onCompleted();         // encerra a chamada
        }

        @Override
        public void consultarSaldo(SaldoRequest request,
                                   StreamObserver<SaldoResponse> responseObserver) {
            System.out.println("[Servidor B] Consulta de saldo: " + request.getConta());
            SaldoResponse resp = SaldoResponse.newBuilder()
                    .setConta(request.getConta())
                    .setSaldo(SALDOS.getOrDefault(request.getConta(), 0.0))
                    .setMoeda("BRL")
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        }
    }

    public static void main(String[] args) throws Exception {
        int porta = 50051;
        // ServerBuilder.forPort escuta em 0.0.0.0 (todas as interfaces) por padrão,
        // o que é essencial para aceitar conexões de outra VM na GCP.
        Server server = ServerBuilder.forPort(porta)
                .addService(new PagamentoServiceImpl())
                .build()
                .start();
        System.out.println("[Servidor B] No ar em 0.0.0.0:" + porta + ". Aguardando requisições...\n");
        server.awaitTermination();
    }
}
