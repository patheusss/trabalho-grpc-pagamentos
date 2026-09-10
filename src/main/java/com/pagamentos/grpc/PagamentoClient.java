package com.pagamentos.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * SERVIÇO A — CLIENTE gRPC (Sistema de Pagamentos)
 * Monta requisições e chama os métodos remotos do Serviço B.
 */
public class PagamentoClient {

    static void imprimir(PagamentoResponse r) {
        System.out.println("  <- Resposta do Servidor B:");
        System.out.println("       Transação : " + r.getIdTransacao());
        System.out.println("       Status    : " + r.getStatus());
        System.out.println("       Mensagem  : " + r.getMensagem());
        System.out.printf ("       Saldo restante (origem): %.2f%n", r.getSaldoRestante());
        System.out.println("       Processado em: " + r.getDataHora());
    }

    public static void main(String[] args) throws Exception {
        // Local: localhost:50051 | Na GCP: <IP-EXTERNO-DA-VM-B>:50051
        String alvo = args.length > 0 ? args[0] : "localhost:50051";

        ManagedChannel canal = ManagedChannelBuilder.forTarget(alvo)
                .usePlaintext() // sem TLS (equivale ao insecure_channel do Python)
                .build();

        // Stub síncrono (blocking): a chamada espera a resposta, como pede o enunciado.
        PagamentoServiceGrpc.PagamentoServiceBlockingStub stub =
                PagamentoServiceGrpc.newBlockingStub(canal);

        System.out.println("[Cliente A] Conectado ao Servidor B em " + alvo + "\n");

        // ---- Cenário 1: pagamento APROVADO ----
        System.out.println("[Cliente A] -> Enviando TX-0001: R$100,50 de CONTA-100 para CONTA-200");
        imprimir(stub.processarPagamento(PagamentoRequest.newBuilder()
                .setIdTransacao("TX-0001")
                .setContaOrigem("CONTA-100")
                .setContaDestino("CONTA-200")
                .setValor(100.50)
                .setMoeda("BRL")
                .build()));

        // ---- Cenário 2: SALDO_INSUFICIENTE ----
        System.out.println("\n[Cliente A] -> Enviando TX-0002: R$999,00 de CONTA-300 (saldo 0) para CONTA-100");
        imprimir(stub.processarPagamento(PagamentoRequest.newBuilder()
                .setIdTransacao("TX-0002")
                .setContaOrigem("CONTA-300")
                .setContaDestino("CONTA-100")
                .setValor(999.00)
                .setMoeda("BRL")
                .build()));

        // ---- Cenário 3: consulta de saldo (outro método RPC) ----
        System.out.println("\n[Cliente A] -> Consultando saldo de CONTA-100");
        SaldoResponse s = stub.consultarSaldo(SaldoRequest.newBuilder()
                .setConta("CONTA-100")
                .build());
        System.out.printf("  <- CONTA %s: R$%.2f %s%n", s.getConta(), s.getSaldo(), s.getMoeda());

        canal.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
