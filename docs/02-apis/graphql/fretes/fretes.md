## 📄 Documentação de Descoberta: API GraphQL (Fretes)

### 1\. Objetivo

Padronizar a extração da entidade "Fretes" (tipo `FreightBase` na API) para garantir **100% de integridade de dados** e **correspondência exata (Join)** com os relatórios analíticos extraídos manualmente (`frete_relacao_analitico...csv`). Esta documentação substitui versões anteriores, corrigindo falhas críticas de mapeamento de chaves fiscais e dados aninhados.

### 2\. Metodologia de Descoberta e Validação

O processo de validação (Versão 5.1) identificou e resolveu três bloqueios críticos que impediam a conciliação dos dados:

1.  **Correção do JOIN (Chave de Acesso):**

      * **Falha:** A tentativa de usar `referenceNumber` ou `id` resultava em 0% de cruzamento com o CSV manual.
      * **Descoberta:** Via Introspection, localizamos o objeto aninhado `cte`.
      * **Correção:** A chave de 44 dígitos (PK real) reside em `cte { key }`.

2.  **Dados de Notas Fiscais (NFs):**

      * **Falha:** A lista `freightInvoices` parecia vazia ou sem detalhes na raiz.
      * **Descoberta:** A estrutura correta é uma tabela de ligação (pivô).
      * **Correção:** Os dados reais estão em `freightInvoices { invoice { number, series, value } }`.

3.  **Ambiguidade de Valores Financeiros:**

      * **Análise:** A API separa estritamente `subtotal` (Frete Peso) de `total` (Frete + Taxas/Impostos). O relatório manual muitas vezes replica o valor total na coluna de frete.
      * **Validação:** O campo `total` da API foi validado com precisão de centavos em relação ao CSV.

-----

### 3\. Configuração Final no Insomnia (Produção)

Utilize esta configuração para a extração massiva dos dados.

#### 3.1. Pasta

`API GraphQL / Fretes / Produção`

#### 3.2. Requisição

  * **Nome:** `[PRODUÇÃO] Extração Fretes Full Schema (V5.1)`
  * **Método:** `POST`
  * **URL:** `{{base_url}}/graphql`

#### 3.3. Body (Query GraphQL Validada)

```graphql
query BuscarFretesProducaoV5_1($params: FreightInput!, $after: String) {
  freight(params: $params, after: $after, first: 100) {
    edges {
      node {
        # --- 1. Identificadores e Chaves ---
        id              # ID Interno
        referenceNumber # Referência da Rota/Cliente
        serviceAt       # Data de Emissão

        # [CRÍTICO] Objeto Fiscal CT-e (Correção do Join)
        cte {
          key     # <--- CHAVE DE 44 DÍGITOS (JOIN)
          number  # Número do CT-e
          series  # Série
        }

        # --- 2. Valores Financeiros ---
        total           # Valor Total do Serviço (Validado)
        subtotal        # Valor do Frete Peso
        invoicesValue   # Valor da Carga (Soma NFs)

        # --- 3. Métricas Físicas ---
        taxedWeight          # Kg Taxado
        realWeight           # Kg Real
        totalCubicVolume     # M3 (Cubagem)
        invoicesTotalVolumes # Volumes

        # --- 4. Notas Fiscais (Lista Aninhada) ---
        freightInvoices {
          invoice {
            number
            series
            key     # Chave da NF-e
            value   # Valor da Nota
          }
        }

        # --- 5. Atores e Endereços (Expandidos) ---
        sender {
          name
          mainAddress { city { name state { code } } }
        }
        receiver {
          name
          mainAddress { city { name state { code } } }
        }
        payer { name }
        
        # --- 6. Classificadores ---
        modal           # Tipo String (ex: "rodo")
        corporation { name }           # Filial
        customerPriceTable { name }    # Tabela de Preço
        freightClassification { name } # Classificação
        costCenter { name }            # Centro de Custo
      }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}
```

#### 3.4. Variables (JSON)

```json
{
  "params": {
    "serviceAt": "{{data_inicio}} - {{data_fim}}"
  }
}
```

#### 3.5. Headers

| Header | Valor |
| :--- | :--- |
| `Authorization` | `Bearer {{token_graphql}}` |
| `Content-Type` | `application/json` |

-----

### 4\. Análise de Cobertura de Schema (CSV vs. API)

O mapeamento estrutural entre o CSV de origem e a query GraphQL expandida foi validado comparando a API (V5.1) contra o arquivo `frete_relacao_analitico_21-11-2025.csv`.

| Coluna CSV (Manual) | Caminho JSON (API) | Status Validação |
| :--- | :--- | :--- |
| `Chave CT-e` | `cte { key }` | ✅ **100% (JOIN)** |
| `Nº CT-e` | `cte { number }` | ✅ **Validado** |
| `Série` | `cte { series }` | ✅ **Validado** |
| `Data frete` | `serviceAt` | ✅ **Validado** |
| `Valor Total do Serviço`| `total` | ✅ **100% Exato** |
| `Valor Frete` | `subtotal` | ✅ **Validado** |
| `Valor NF` | `invoicesValue` | ✅ **100% Exato** |
| `Kg Taxado` | `taxedWeight` | ✅ **Validado** |
| `Kg Real` | `realWeight` | ✅ **Validado** |
| `M3` | `totalCubicVolume` | ✅ **Validado** |
| `NF` | `freightInvoices { invoice { number } }` | ✅ **Mapeado (Lista)** |
| `Filial` | `corporation { name }` | ✅ **Validado** |
| `Remetente` | `sender { name }` | ✅ **Validado** |
| `Destinatario` | `receiver { name }` | ✅ **Validado** |
| `Pagador` | `payer { name }` | ✅ **Validado** |
| `Origem / UF` | `sender { mainAddress { city { name } } }` | ✅ **Validado** |
| `Destino / UF` | `receiver { mainAddress { city { name } } }`| ✅ **Validado** |

### 5\. Conclusão

A validação técnica foi concluída com sucesso.

  * **Cobertura:** 100% dos campos críticos mapeados.
  * **Integridade:** Join perfeito utilizando a chave de 44 dígitos oculta no objeto `cte`.
  * **Status:** Aprovado para implementação em produção no pipeline de dados.


  {
	"data": {
		"freight": {
			"edges": [
				{
					"node": {
						"id": 42551850,
						"referenceNumber": "RJ X NITERÓI ",
						"serviceAt": "2025-11-21T07:47:00-03:00",
						"cte": {
							"key": "33251160960473001304570030000017781255726754",
							"number": 1778,
							"series": 3
						},
						"total": 111.18,
						"subtotal": 86.72,
						"taxedWeight": 54.825,
						"realWeight": 26.2,
						"totalCubicVolume": 0.18275,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 1985.9,
						"freightInvoices": [
							{
								"invoice": {
									"number": "322",
									"series": "1",
									"key": "33251117774419000101550010000003221630745914",
									"value": 1985.9,
									"weight": 26.2
								}
							}
						],
						"sender": {
							"name": "W I N S MARQUES DISTRIBUIDORA DE PRODUTOS E SERVICOS EPP",
							"mainAddress": {
								"city": {
									"name": "Rio de Janeiro",
									"state": {
										"code": "RJ"
									}
								}
							}
						},
						"receiver": {
							"name": "RENATA DE OLIVEIRA BENDIA ",
							"mainAddress": {
								"city": {
									"name": "Maricá",
									"state": {
										"code": "RJ"
									}
								}
							}
						},
						"payer": {
							"name": "W I N S MARQUES DISTRIBUIDORA DE PRODUTOS E SERVICOS EPP"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "PROMOCIONAL RJR"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42552619,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T08:17:00-03:00",
						"cte": {
							"key": "35251160960473001134570030000350131602694953",
							"number": 35013,
							"series": 3
						},
						"total": 400.0,
						"subtotal": 352.0,
						"taxedWeight": 162.0,
						"realWeight": 162.0,
						"totalCubicVolume": 0.51948,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 7627.68,
						"freightInvoices": [
							{
								"invoice": {
									"number": "7301",
									"series": "1",
									"key": "35251013638872000121550010000073011801129239",
									"value": 7627.68,
									"weight": 162.0
								}
							}
						],
						"sender": {
							"name": "BIOSANUS INDUSTRIA DE SUPLEMENTOS ALIMENTARES LTDA",
							"mainAddress": {
								"city": {
									"name": "Agudos",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "BIOSANUS INDUSTRIA DE SUPLEMENTOS ALIMENTARES LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "LABORATORIO FARMACEUTICO TAUHERE O.H. LTDA"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42552825,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T08:20:00-03:00",
						"cte": {
							"key": "35251160960473001134570030000350141309821120",
							"number": 35014,
							"series": 3
						},
						"total": 100.09,
						"subtotal": 100.09,
						"taxedWeight": 30.0,
						"realWeight": 30.0,
						"totalCubicVolume": 0.0,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 5.16,
						"freightInvoices": [
							{
								"invoice": {
									"number": "263913",
									"series": "3",
									"key": "35251144519650000113550030002639131497937193",
									"value": 5.16,
									"weight": 30.0
								}
							}
						],
						"sender": {
							"name": "PIZATTO & CIA LTDA",
							"mainAddress": {
								"city": {
									"name": "Dois Córregos",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "PIZATTO & CIA LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "PIZATTO"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42558831,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T10:12:00-03:00",
						"cte": {
							"key": "35251160960473000243570030000874851997387531",
							"number": 87485,
							"series": 3
						},
						"total": 78.02,
						"subtotal": 68.66,
						"taxedWeight": 50.5,
						"realWeight": 50.5,
						"totalCubicVolume": 0.129654,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 878.09,
						"freightInvoices": [
							{
								"invoice": {
									"number": "22549",
									"series": "1",
									"key": "41251110345032000182550010000225491655450964",
									"value": 878.09,
									"weight": 50.5
								}
							}
						],
						"sender": {
							"name": "OGGI COMERCIO DE MOVEIS LTDA",
							"mainAddress": {
								"city": {
									"name": "São José dos Pinhais",
									"state": {
										"code": "PR"
									}
								}
							}
						},
						"receiver": {
							"name": "OGGI COMERCIO DE MOVEIS LTDA",
							"mainAddress": {
								"city": {
									"name": "São José dos Pinhais",
									"state": {
										"code": "PR"
									}
								}
							}
						},
						"payer": {
							"name": "OGGI COMERCIO DE MOVEIS LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "OGGI COMERCIO DE MOVEIS"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42560244,
						"referenceNumber": "DUQUE DE CAXIAS X SP",
						"serviceAt": "2025-11-21T10:19:00-03:00",
						"cte": {
							"key": "33251160960473001304570030000017791526189005",
							"number": 1779,
							"series": 3
						},
						"total": 213.43,
						"subtotal": 213.43,
						"taxedWeight": 92.0,
						"realWeight": 92.0,
						"totalCubicVolume": 0.2737,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 17698.5,
						"freightInvoices": [
							{
								"invoice": {
									"number": "31237",
									"series": "55",
									"key": "33251129348695000189550550000312371570000036",
									"value": 17698.5,
									"weight": 92.0
								}
							}
						],
						"sender": {
							"name": "SACOR SIDEROTECNICA LTDA",
							"mainAddress": {
								"city": {
									"name": "Duque de Caxias",
									"state": {
										"code": "RJ"
									}
								}
							}
						},
						"receiver": {
							"name": "JAPKS LOGISTICA E TRANSPORTES LTDA",
							"mainAddress": {
								"city": {
									"name": "São Paulo",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"payer": {
							"name": "JAPKS LOGISTICA E TRANSPORTES LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "JAPKS"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42561469,
						"referenceNumber": "GUARULHOS X BARIRI",
						"serviceAt": "2025-11-21T10:40:00-03:00",
						"cte": {
							"key": "35251151863654000260570010000000301916680754",
							"number": 30,
							"series": 1
						},
						"total": 5200.0,
						"subtotal": 4576.0,
						"taxedWeight": 4875.0,
						"realWeight": 3000.0,
						"totalCubicVolume": 16.25,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 1000000.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "1907",
									"series": "1",
									"key": "35251148612683000164550010000019071595248798",
									"value": 195137.0,
									"weight": 150.0
								}
							},
							{
								"invoice": {
									"number": "1908",
									"series": "1",
									"key": "35251148612683000164550010000019081246124144",
									"value": 804863.0,
									"weight": 3300.0
								}
							}
						],
						"sender": {
							"name": "SILME INDUSTRIA DE PRENSAS HIDRAULICAS LTDA",
							"mainAddress": {
								"city": {
									"name": "Guarulhos",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "HIDRODOMI DO BRASIL INDUSTRIA E COMERCIO LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "TRANSPORTADORA RODOGARCIA LTDA"
						},
						"customerPriceTable": {
							"name": "HIDRODOMI DO BRASIL"
						},
						"freightClassification": {
							"name": "FECHADO - FTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42562545,
						"referenceNumber": "RJ X SP",
						"serviceAt": "2025-11-21T10:46:00-03:00",
						"cte": {
							"key": "33251160960473001304570030000017801792993962",
							"number": 1780,
							"series": 3
						},
						"total": 302.21,
						"subtotal": 302.21,
						"taxedWeight": 264.0,
						"realWeight": 264.0,
						"totalCubicVolume": 0.52,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 6004.48,
						"freightInvoices": [
							{
								"invoice": {
									"number": "61930",
									"series": "3",
									"key": "33251142200550000102550030000619301328115284",
									"value": 6004.48,
									"weight": 263.82
								}
							}
						],
						"sender": {
							"name": "COGUMELO INDUSTRIA E COMERCIO LTDA",
							"mainAddress": {
								"city": {
									"name": "Rio de Janeiro",
									"state": {
										"code": "RJ"
									}
								}
							}
						},
						"receiver": {
							"name": "JAPKS LOGISTICA E TRANSPORTES LTDA",
							"mainAddress": {
								"city": {
									"name": "São Paulo",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"payer": {
							"name": "JAPKS LOGISTICA E TRANSPORTES LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "JAPKS"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42561993,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T10:47:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031551288078491",
							"number": 3155,
							"series": 3
						},
						"total": 561.05,
						"subtotal": 493.72,
						"taxedWeight": 303.0,
						"realWeight": 303.0,
						"totalCubicVolume": 0.495,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 5760.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "79404",
									"series": "100",
									"key": "43251187822110000117551000000794041000072362",
									"value": 5760.0,
									"weight": 303.0
								}
							}
						],
						"sender": {
							"name": "INDUSTRIA QUIMICA MASCIA LTDA",
							"mainAddress": {
								"city": {
									"name": "Caxias do Sul",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "HIDRODOMI DO BRASIL INDUSTRIA E COMERCIO LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "HIDRODOMI DO BRASIL"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42563624,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T10:50:00-03:00",
						"cte": {
							"key": "35251160960473000243570030000875531040199537",
							"number": 87553,
							"series": 3
						},
						"total": 836.39,
						"subtotal": 667.94,
						"taxedWeight": 1560.0,
						"realWeight": 1560.0,
						"totalCubicVolume": 1.32,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 9195.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "152950",
									"series": "1",
									"key": "35251160755519000101550010001529501037836800",
									"value": 9195.0,
									"weight": 1560.0
								}
							}
						],
						"sender": {
							"name": "USIQUIMICA DO BRASIL LTDA",
							"mainAddress": {
								"city": {
									"name": "Guarulhos",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "KEMIRA CHEMICALS BRASIL LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "KEMIRA - FRACIONADA"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42562599,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T10:53:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031571027659312",
							"number": 3157,
							"series": 3
						},
						"total": 203.84,
						"subtotal": 203.84,
						"taxedWeight": 208.0,
						"realWeight": 208.0,
						"totalCubicVolume": 0.0,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 2980.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "23669",
									"series": "0",
									"key": "43251192599901000160550000000236691536092643",
									"value": 2980.0,
									"weight": 208.0
								}
							}
						],
						"sender": {
							"name": "RUBBERSUL IND. E COM. DE ARTEF. DE BORRACHA LTDA.",
							"mainAddress": {
								"city": {
									"name": "Estância Velha",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "RUBBERSUL IND. E COM. DE ARTEF. DE BORRACHA LTDA."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "RUBBERSUL - PORTO ALEGRE"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42562745,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T10:58:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031581304128372",
							"number": 3158,
							"series": 3
						},
						"total": 154.92,
						"subtotal": 136.33,
						"taxedWeight": 12.48,
						"realWeight": 4.0,
						"totalCubicVolume": 0.0416,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 2976.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "3681",
									"series": "1",
									"key": "43251108222899000107550010000036811685113972",
									"value": 470.0,
									"weight": 2.0
								}
							},
							{
								"invoice": {
									"number": "3682",
									"series": "1",
									"key": "43251108222899000107550010000036821212299502",
									"value": 470.0,
									"weight": 2.0
								}
							},
							{
								"invoice": {
									"number": "3683",
									"series": "1",
									"key": "43251108222899000107550010000036831572955239",
									"value": 1040.0,
									"weight": 2.0
								}
							},
							{
								"invoice": {
									"number": "3697",
									"series": "1",
									"key": "43251108222899000107550010000036971230948285",
									"value": 996.0,
									"weight": 2.0
								}
							}
						],
						"sender": {
							"name": "JONAS PIETRO JUNQUEIRA FIGUEIRO LTDA",
							"mainAddress": {
								"city": {
									"name": "Montenegro",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "JONAS PIETRO JUNQUEIRA FIGUEIRO LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "PADRÃO"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42562916,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T10:58:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031591314988193",
							"number": 3159,
							"series": 3
						},
						"total": 824.62,
						"subtotal": 824.62,
						"taxedWeight": 1248.0,
						"realWeight": 1248.0,
						"totalCubicVolume": 0.0,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 17952.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "23670",
									"series": "0",
									"key": "43251192599901000160550000000236701354960020",
									"value": 17952.0,
									"weight": 1248.0
								}
							}
						],
						"sender": {
							"name": "RUBBERSUL IND. E COM. DE ARTEF. DE BORRACHA LTDA.",
							"mainAddress": {
								"city": {
									"name": "Estância Velha",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "RUBBERSUL IND. E COM. DE ARTEF. DE BORRACHA LTDA."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "RUBBERSUL - PORTO ALEGRE"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42562959,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:02:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031601295585670",
							"number": 3160,
							"series": 3
						},
						"total": 147.17,
						"subtotal": 129.51,
						"taxedWeight": 5.454,
						"realWeight": 4.0,
						"totalCubicVolume": 0.01818,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 3552.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "3684",
									"series": "1",
									"key": "43251108222899000107550010000036841600672698",
									"value": 1560.0,
									"weight": 2.0
								}
							},
							{
								"invoice": {
									"number": "3695",
									"series": "1",
									"key": "43251108222899000107550010000036951688878924",
									"value": 1992.0,
									"weight": 4.0
								}
							}
						],
						"sender": {
							"name": "JONAS PIETRO JUNQUEIRA FIGUEIRO LTDA",
							"mainAddress": {
								"city": {
									"name": "Montenegro",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "JONAS PIETRO JUNQUEIRA FIGUEIRO LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "PADRÃO"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42563239,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:04:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031611730695230",
							"number": 3161,
							"series": 3
						},
						"total": 165.42,
						"subtotal": 165.42,
						"taxedWeight": 218.0,
						"realWeight": 218.0,
						"totalCubicVolume": 0.0,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 3359.0,
						"freightInvoices": [
							{
								"invoice": {
									"number": "23671",
									"series": "0",
									"key": "43251192599901000160550000000236711862896448",
									"value": 3359.0,
									"weight": 218.0
								}
							}
						],
						"sender": {
							"name": "RUBBERSUL IND. E COM. DE ARTEF. DE BORRACHA LTDA.",
							"mainAddress": {
								"city": {
									"name": "Estância Velha",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "RUBBERSUL IND. E COM. DE ARTEF. DE BORRACHA LTDA."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "RUBBERSUL - PORTO ALEGRE"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42564007,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:17:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031621307099201",
							"number": 3162,
							"series": 3
						},
						"total": 203.77,
						"subtotal": 189.51,
						"taxedWeight": 64.552,
						"realWeight": 64.552,
						"totalCubicVolume": 0.648,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 2314.5,
						"freightInvoices": [
							{
								"invoice": {
									"number": "597070",
									"series": "4",
									"key": "43251103746938001387550040005970701645932295",
									"value": 2314.5,
									"weight": 64.552
								}
							}
						],
						"sender": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A.",
							"mainAddress": {
								"city": {
									"name": "São Leopoldo",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": {
							"name": "BANCO DO BRASIL S/A",
							"mainAddress": {
								"city": {
									"name": "Jaboatão dos Guararapes",
									"state": {
										"code": "PE"
									}
								}
							}
						},
						"payer": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "BR SUPPLY"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42564413,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:21:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031631874303288",
							"number": 3163,
							"series": 3
						},
						"total": 182.33,
						"subtotal": 169.57,
						"taxedWeight": 30.922,
						"realWeight": 30.922,
						"totalCubicVolume": 0.283,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 863.34,
						"freightInvoices": [
							{
								"invoice": {
									"number": "596576",
									"series": "4",
									"key": "43251103746938001387550040005965761103307217",
									"value": 863.34,
									"weight": 30.922
								}
							}
						],
						"sender": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A.",
							"mainAddress": {
								"city": {
									"name": "São Leopoldo",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": {
							"name": "BANCO DO BRASIL S/A",
							"mainAddress": {
								"city": {
									"name": "Moreno",
									"state": {
										"code": "PE"
									}
								}
							}
						},
						"payer": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "BR SUPPLY"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42564760,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:24:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031661689751239",
							"number": 3166,
							"series": 3
						},
						"total": 127.95,
						"subtotal": 112.6,
						"taxedWeight": 17.39,
						"realWeight": 17.39,
						"totalCubicVolume": 0.0,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 727.76,
						"freightInvoices": [
							{
								"invoice": {
									"number": "596870",
									"series": "4",
									"key": "43251103746938001387550040005968701585401182",
									"value": 727.76,
									"weight": 17.39
								}
							}
						],
						"sender": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A.",
							"mainAddress": {
								"city": {
									"name": "São Leopoldo",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": {
							"name": "SAPORE S.A.",
							"mainAddress": {
								"city": {
									"name": "São Paulo",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"payer": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "BR SUPPLY"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42564839,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:26:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031641314265620",
							"number": 3164,
							"series": 3
						},
						"total": 372.27,
						"subtotal": 327.6,
						"taxedWeight": 437.425,
						"realWeight": 437.425,
						"totalCubicVolume": 2.47,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 4938.37,
						"freightInvoices": [
							{
								"invoice": {
									"number": "597268",
									"series": "4",
									"key": "43251103746938001387550040005972681135759926",
									"value": 4938.37,
									"weight": 437.425
								}
							}
						],
						"sender": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A.",
							"mainAddress": {
								"city": {
									"name": "São Leopoldo",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": {
							"name": "PROSEGUR BRASIL SA TRANSPORTADORA D E SEGURANCA",
							"mainAddress": {
								"city": {
									"name": "Curitiba",
									"state": {
										"code": "PR"
									}
								}
							}
						},
						"payer": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "BR SUPPLY"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42564959,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:27:00-03:00",
						"cte": {
							"key": "43251160960473001568570030000031651081586338",
							"number": 3165,
							"series": 3
						},
						"total": 96.2,
						"subtotal": 84.66,
						"taxedWeight": 4.492,
						"realWeight": 4.492,
						"totalCubicVolume": 0.027,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 277.29,
						"freightInvoices": [
							{
								"invoice": {
									"number": "596736",
									"series": "4",
									"key": "43251103746938001387550040005967361521868856",
									"value": 277.29,
									"weight": 4.492
								}
							}
						],
						"sender": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A.",
							"mainAddress": {
								"city": {
									"name": "São Leopoldo",
									"state": {
										"code": "RS"
									}
								}
							}
						},
						"receiver": {
							"name": "MULTILOG BRASIL S/A",
							"mainAddress": {
								"city": {
									"name": "São José dos Pinhais",
									"state": {
										"code": "PR"
									}
								}
							}
						},
						"payer": {
							"name": "BRS SUPRIMENTOS CORPORATIVOS S.A."
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "BR SUPPLY"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				},
				{
					"node": {
						"id": 42565912,
						"referenceNumber": "",
						"serviceAt": "2025-11-21T11:36:00-03:00",
						"cte": {
							"key": "35251160960473001134570030000350301773733628",
							"number": 35030,
							"series": 3
						},
						"total": 2313.98,
						"subtotal": 2152.0,
						"taxedWeight": 438.0,
						"realWeight": 438.0,
						"totalCubicVolume": 0.0,
						"invoicesTotalVolumes": 0,
						"invoicesValue": 14790.01,
						"freightInvoices": [
							{
								"invoice": {
									"number": "25147",
									"series": "1",
									"key": "35251109437423000148550010000251471737101005",
									"value": 14790.01,
									"weight": 438.0
								}
							}
						],
						"sender": {
							"name": "AROMALLIS INDUSTRIA E COMERCIO DE FRAGRANCIAS LTDA",
							"mainAddress": {
								"city": {
									"name": "Torrinha",
									"state": {
										"code": "SP"
									}
								}
							}
						},
						"receiver": null,
						"payer": {
							"name": "AROMALLIS INDUSTRIA E COMERCIO DE FRAGRANCIAS LTDA"
						},
						"modal": "rodo",
						"corporation": {
							"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
						},
						"customerPriceTable": {
							"name": "ESPECIAL NÍVEL BRASIL"
						},
						"freightClassification": {
							"name": "FRACIONADO - LTL"
						},
						"costCenter": null
					}
				}
			],
			"pageInfo": {
				"hasNextPage": true,
				"endCursor": "MjA"
			}
		}
	}
}