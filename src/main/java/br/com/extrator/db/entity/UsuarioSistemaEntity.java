/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/UsuarioSistemaEntity.java
Classe  : UsuarioSistemaEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de usuario sistema entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
- getUserId(): expone valor atual do estado interno.
- setUserId(...1 args): ajusta valor em estado interno.
- getNome(): expone valor atual do estado interno.
- setNome(...1 args): ajusta valor em estado interno.
- getDataAtualizacao(): expone valor atual do estado interno.
- setDataAtualizacao(...1 args): ajusta valor em estado interno.
Atributos-chave:
- userId: campo de estado para "user id".
- nome: campo de estado para "nome".
- dataAtualizacao: campo de estado para "data atualizacao".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.time.LocalDateTime;

/**
 * Entity (Entidade) que representa uma linha na tabela 'dim_usuarios_sistema' do banco de dados.
 * Tabela dimensão para armazenar informações de usuários do sistema (Individual).
 */
public class UsuarioSistemaEntity {

    private Long userId;
    private String nome;
    private LocalDateTime dataAtualizacao;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(final String nome) {
        this.nome = nome;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(final LocalDateTime dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }
}
