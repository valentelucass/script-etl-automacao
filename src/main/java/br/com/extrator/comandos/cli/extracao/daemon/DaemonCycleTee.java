/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/extracao/daemon/DaemonCycleTee.java
Classe  : DaemonCycleTee (utility), CycleTeeHandle (nested), TeeOutputStream (nested)
Pacote  : br.com.extrator.comandos.cli.extracao.daemon
Modulo  : CLI - Daemon
Papel   : Implementa Tee pattern para duplicar stdout/stderr para arquivo durante ciclos daemon.
Conecta com: Sem dependencia interna
Fluxo geral:
1) abrir() cria PrintStreams tee e alterna System.out/err
2) TeeOutputStream copia bytes para dois outputs (console + arquivo)
3) CycleTeeHandle restaura System.out/err ao close()
Estrutura interna:
Atributos: originalOut, originalErr, teeOut, teeErr, arquivoOut, arquivoErr (em CycleTeeHandle)
Metodos: abrir() [static], close(), write()
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.extracao.daemon;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class DaemonCycleTee {
    private DaemonCycleTee() {
    }

    static AutoCloseable abrir(final Path cicloLog) throws IOException {
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;
        final FileOutputStream arquivoOut = new FileOutputStream(cicloLog.toFile(), true);
        final FileOutputStream arquivoErr = new FileOutputStream(cicloLog.toFile(), true);
        final PrintStream teeOut = new PrintStream(new TeeOutputStream(originalOut, arquivoOut), true, StandardCharsets.UTF_8);
        final PrintStream teeErr = new PrintStream(new TeeOutputStream(originalErr, arquivoErr), true, StandardCharsets.UTF_8);
        System.setOut(teeOut);
        System.setErr(teeErr);
        return new CycleTeeHandle(originalOut, originalErr, teeOut, teeErr, arquivoOut, arquivoErr);
    }

    private static final class CycleTeeHandle implements AutoCloseable {
        private final PrintStream originalOut;
        private final PrintStream originalErr;
        private final PrintStream teeOut;
        private final PrintStream teeErr;
        private final FileOutputStream arquivoOut;
        private final FileOutputStream arquivoErr;

        private CycleTeeHandle(final PrintStream originalOut,
                               final PrintStream originalErr,
                               final PrintStream teeOut,
                               final PrintStream teeErr,
                               final FileOutputStream arquivoOut,
                               final FileOutputStream arquivoErr) {
            this.originalOut = originalOut;
            this.originalErr = originalErr;
            this.teeOut = teeOut;
            this.teeErr = teeErr;
            this.arquivoOut = arquivoOut;
            this.arquivoErr = arquivoErr;
        }

        @Override
        public void close() {
            System.setOut(originalOut);
            System.setErr(originalErr);
            teeOut.flush();
            teeErr.flush();
            teeOut.close();
            teeErr.close();
            try {
                arquivoOut.close();
            } catch (final IOException ignored) {
                // no-op
            }
            try {
                arquivoErr.close();
            } catch (final IOException ignored) {
                // no-op
            }
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        private TeeOutputStream(final OutputStream out1, final OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(final int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }
    }
}
