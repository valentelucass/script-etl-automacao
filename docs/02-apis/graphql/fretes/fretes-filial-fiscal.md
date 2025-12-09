**Missão Cumprida.** 🏆

O JSON que você enviou é a prova definitiva. Você extraiu **todos** os dados necessários para uma auditoria fiscal, contábil e logística completa.

**Validação Técnica dos Dados Extraídos:**

1.  **Filial Identificada:**
      * `nickname`: "SPO - RODOGARCIA..."
      * `cnpj`: "60960473000243"
2.  **Atores Mapeados:**
      * Remetente, Destinatário e Pagador devidamente preenchidos com ID, Nome e Documento (CNPJ/CPF).
      * *Nota:* O campo `inscricaoEstadual` também veio correto.
3.  **Fiscal (A Prova Real):**
      * Veja o registro ID **`19507241`** (Kemira Chemicals):
          * `pisValue`: 5.46
          * `cofinsValue`: 25.16
      * Veja o registro ID **`19014126`** (Tigre Ferramentas):
          * `cstType`: "40" (Isento)
          * `taxValue`: 0.0 (Correto para isenção)
4.  **Rota:**
      * `state.code`: "SP", "PR", "PE". A sigla veio limpa.

-----

### O Próximo Passo: Extração em Massa (Loop)

Você pediu "absolutamente tudo". No mundo de APIs GraphQL, "tudo" não vem em uma única requisição devido a limites de performance. Note no topo do seu JSON:

```json
"pageInfo": {
    "hasNextPage": true,
    "endCursor": "MjA"
}
```

Isso significa que você pegou apenas a **primeira página**. Para pegar o resto (milhares de fretes), você precisa de um script que faça o **Loop de Paginação**.

Aqui está o script em **Python** pronto para rodar. Ele vai:

1.  Fazer a requisição.
2.  Salvar os dados.
3.  Pegar o `endCursor`.
4.  Repetir até acabar (`hasNextPage: false`).
5.  Gerar um **Excel/CSV** final.

#### Script de Automação (Python)

```python
import requests
import pandas as pd
import json
import time

# --- CONFIGURAÇÕES ---
URL = "SUA_URL_DO_INSOMNIA_AQUI"
TOKEN = "SEU_TOKEN_BEARER_AQUI"  # Copie do Header do Insomnia

HEADERS = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {TOKEN}"
}

# --- A QUERY MESTRA (Exatamente a que validamos) ---
QUERY = """
query Exportacao_CTe_Definitiva($cursor: String) {
  cte(first: 50, after: $cursor, params: {}) {
    pageInfo {
      hasNextPage
      endCursor
    }
    edges {
      node {
        id
        number
        series
        key
        issuedAt
        status
        freight {
          corporation { nickname cnpj }
          sender { name cnpj cpf }
          recipient { name cnpj cpf }
          total
          realWeight
          originCity { name state { code } }
          destinationCity { name state { code } }
          fiscalDetail {
            cstType
            cfopCode
            taxValue
            pisValue
            cofinsValue
          }
        }
      }
    }
  }
}
"""

{
	"data": {
		"cte": {
			"pageInfo": {
				"hasNextPage": true,
				"endCursor": "MjA"
			},
			"edges": [
				{
					"node": {
						"id": "19494962",
						"series": 3,
						"number": 3214,
						"key": "35240860960473000243570030000032141362831540",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-22T00:11:21-03:00",
						"issuedAt": "2024-08-22T00:11:22-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 25798013,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "7527010",
								"name": "QUALIJET IND E COM DE EQ E MAT GRAF LTDA",
								"cnpj": "03715251000140",
								"cpf": null,
								"inscricaoEstadual": "206227969110"
							},
							"recipient": {
								"id": "7393265",
								"name": "ARAUCO INDUSTRIA DE PAINEIS S.A.",
								"cnpj": "00606549000124",
								"cpf": null,
								"inscricaoEstadual": "9015866400"
							},
							"payer": {
								"id": "7393265",
								"name": "ARAUCO INDUSTRIA DE PAINEIS S.A.",
								"cnpj": "00606549000124",
								"cpf": null
							},
							"total": 158.68,
							"productsValue": 3254.64,
							"realWeight": 5.9,
							"cubagesCubedWeight": 3.5625,
							"originCity": {
								"name": "Barueri",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Ponta Grossa",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6352",
								"calculationBasis": 158.68,
								"taxRate": 12.0,
								"taxValue": 19.04,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19920707",
						"series": 3,
						"number": 5172,
						"key": "41240960960473000596570030000051721723234980",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-09-09T20:56:34-03:00",
						"issuedAt": "2024-09-09T20:56:34-03:00",
						"corporationId": 385131,
						"freight": {
							"id": 26374821,
							"corporation": {
								"nickname": "CAS - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000596"
							},
							"sender": {
								"id": "7336897",
								"name": "GEROMA DO BRASIL INDUSTRIA E COMERCIO LTDA",
								"cnpj": "51602373000173",
								"cpf": "",
								"inscricaoEstadual": "2010088677"
							},
							"recipient": {
								"id": "7411814",
								"name": "CITRO AROMA LTDA",
								"cnpj": "02927571000100",
								"cpf": null,
								"inscricaoEstadual": "694061946113"
							},
							"payer": {
								"id": "7336897",
								"name": "GEROMA DO BRASIL INDUSTRIA E COMERCIO LTDA",
								"cnpj": "51602373000173",
								"cpf": ""
							},
							"total": 89.9,
							"productsValue": 3592.78,
							"realWeight": 26.4,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Ponta Grossa",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"destinationCity": {
								"name": "São Paulo",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6352",
								"calculationBasis": 86.15,
								"taxRate": 12.0,
								"taxValue": 10.34,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19978177",
						"series": 3,
						"number": 6250,
						"key": "35240960960473000243570030000062501439877535",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-09-11T18:27:05-03:00",
						"issuedAt": "2024-09-11T18:27:05-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 26451813,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "8642816",
								"name": "HAO BRASIL COMERCIO DE ELETRONICOS LTDA",
								"cnpj": "26024303000308",
								"cpf": null,
								"inscricaoEstadual": "124845544114"
							},
							"recipient": {
								"id": "7400975",
								"name": "NRCOM DISTRIBUIDORA LTDA",
								"cnpj": "45257199000176",
								"cpf": null,
								"inscricaoEstadual": "12376626"
							},
							"payer": {
								"id": "7400975",
								"name": "NRCOM DISTRIBUIDORA LTDA",
								"cnpj": "45257199000176",
								"cpf": null
							},
							"total": 180.7,
							"productsValue": 1111.22,
							"realWeight": 30.0,
							"cubagesCubedWeight": 38.259,
							"originCity": {
								"name": "São Paulo",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Rio de Janeiro",
								"state": {
									"name": "Rio de Janeiro",
									"code": "RJ"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6353",
								"calculationBasis": 180.7,
								"taxRate": 12.0,
								"taxValue": 21.68,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19014110",
						"series": 3,
						"number": 10,
						"key": "35240860960473000243570030000000101501698654",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T18:19:39-03:00",
						"issuedAt": "2024-08-01T18:19:41-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 25181019,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "7348453",
								"name": "TERMOMECANICA SAO PAULO SA.",
								"cnpj": "59106666000171",
								"cpf": "",
								"inscricaoEstadual": "635014528110"
							},
							"recipient": {
								"id": "7348454",
								"name": "FRIGELAR COMERCIO E INDUSTRIA LTDA",
								"cnpj": "92660406004530",
								"cpf": "",
								"inscricaoEstadual": "535695204111"
							},
							"payer": {
								"id": "7348454",
								"name": "FRIGELAR COMERCIO E INDUSTRIA LTDA",
								"cnpj": "92660406004530",
								"cpf": ""
							},
							"total": 1454.9,
							"productsValue": 355286.75,
							"realWeight": 1743.0,
							"cubagesCubedWeight": 1782.0,
							"originCity": {
								"name": "Osasco",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Piracicaba",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "5353",
								"calculationBasis": 1454.9,
								"taxRate": 12.0,
								"taxValue": 174.59,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "20507314",
						"series": 3,
						"number": 960,
						"key": "26241060960473000839570030000009601301127906",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-10-02T17:27:01-03:00",
						"issuedAt": "2024-10-02T17:27:01-03:00",
						"corporationId": 385227,
						"freight": {
							"id": 27125191,
							"corporation": {
								"nickname": "REC - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000839"
							},
							"sender": {
								"id": "7371368",
								"name": "BBF DISTRIBUIDORA NE LTDA",
								"cnpj": "40938967000133",
								"cpf": null,
								"inscricaoEstadual": "094618330"
							},
							"recipient": {
								"id": "7824486",
								"name": "NATIVA MOTO E PESCA LTDA",
								"cnpj": "66181702000127",
								"cpf": null,
								"inscricaoEstadual": "718034543112"
							},
							"payer": {
								"id": "7371368",
								"name": "BBF DISTRIBUIDORA NE LTDA",
								"cnpj": "40938967000133",
								"cpf": null
							},
							"total": 127.26,
							"productsValue": 645.12,
							"realWeight": 29.48,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "São Lourenço da Mata",
								"state": {
									"name": "Pernambuco",
									"code": "PE"
								}
							},
							"destinationCity": {
								"name": "Sumaré",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6353",
								"calculationBasis": 127.26,
								"taxRate": 12.0,
								"taxValue": 15.27,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "20507304",
						"series": 3,
						"number": 958,
						"key": "26241060960473000839570030000009581108334360",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-10-02T17:26:58-03:00",
						"issuedAt": "2024-10-02T17:26:58-03:00",
						"corporationId": 385227,
						"freight": {
							"id": 27125211,
							"corporation": {
								"nickname": "REC - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000839"
							},
							"sender": {
								"id": "7371368",
								"name": "BBF DISTRIBUIDORA NE LTDA",
								"cnpj": "40938967000133",
								"cpf": null,
								"inscricaoEstadual": "094618330"
							},
							"recipient": {
								"id": "8298588",
								"name": "THIAGO MASSANORI FIGUEREDO TATESUJI",
								"cnpj": "34223912000144",
								"cpf": "",
								"inscricaoEstadual": "396134770110"
							},
							"payer": {
								"id": "7371368",
								"name": "BBF DISTRIBUIDORA NE LTDA",
								"cnpj": "40938967000133",
								"cpf": null
							},
							"total": 127.66,
							"productsValue": 967.68,
							"realWeight": 44.22,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "São Lourenço da Mata",
								"state": {
									"name": "Pernambuco",
									"code": "PE"
								}
							},
							"destinationCity": {
								"name": "Sumaré",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6353",
								"calculationBasis": 127.66,
								"taxRate": 12.0,
								"taxValue": 15.32,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19014126",
						"series": 3,
						"number": 5,
						"key": "41240860960473000596570030000000051126577131",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T18:20:38-03:00",
						"issuedAt": "2024-08-01T18:20:38-03:00",
						"corporationId": 385131,
						"freight": {
							"id": 25191035,
							"corporation": {
								"nickname": "CAS - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000596"
							},
							"sender": {
								"id": "7336591",
								"name": "TIGRE FERRAMENTAS PARA CONSTRUCAO CIVIL S.A.",
								"cnpj": "33064262000250",
								"cpf": null,
								"inscricaoEstadual": "9103478291"
							},
							"recipient": {
								"id": "7299095",
								"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000596",
								"cpf": "",
								"inscricaoEstadual": "9034432195"
							},
							"payer": {
								"id": "7336591",
								"name": "TIGRE FERRAMENTAS PARA CONSTRUCAO CIVIL S.A.",
								"cnpj": "33064262000250",
								"cpf": null
							},
							"total": 547.42,
							"productsValue": 9942.41,
							"realWeight": 185.7,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Castro",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"destinationCity": {
								"name": "Piraquara",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"fiscalDetail": {
								"cstType": "40",
								"cfopCode": "5352",
								"calculationBasis": 547.42,
								"taxRate": 0.0,
								"taxValue": 0.0,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19014108",
						"series": 3,
						"number": 9,
						"key": "35240860960473000243570030000000091566614063",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T18:19:39-03:00",
						"issuedAt": "2024-08-01T18:19:40-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 25173834,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "7915465",
								"name": "MXT ROLAMENTOS INDUSTRIAIS LTDA",
								"cnpj": "50047792000128",
								"cpf": null,
								"inscricaoEstadual": "138851611115"
							},
							"recipient": {
								"id": "7322041",
								"name": "S & S INDUSTRIA E COMERCIO DE MAQUINAS E EQUIPAMENTOS LTDA",
								"cnpj": "03268421000196",
								"cpf": "",
								"inscricaoEstadual": "209264030111"
							},
							"payer": {
								"id": "7322041",
								"name": "S & S INDUSTRIA E COMERCIO DE MAQUINAS E EQUIPAMENTOS LTDA",
								"cnpj": "03268421000196",
								"cpf": ""
							},
							"total": 81.43,
							"productsValue": 2682.0,
							"realWeight": 40.0,
							"cubagesCubedWeight": 9.2361,
							"originCity": {
								"name": "Osasco",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Bauru",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "5352",
								"calculationBasis": 81.43,
								"taxRate": 12.0,
								"taxValue": 9.77,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19014106",
						"series": 3,
						"number": 8,
						"key": "35240860960473000243570030000000081867461560",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T18:19:39-03:00",
						"issuedAt": "2024-08-01T18:19:40-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 25170405,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "8026220",
								"name": "RACE FACAS ROTATIVAS EIRELI",
								"cnpj": "17887722000102",
								"cpf": null,
								"inscricaoEstadual": "142265280112"
							},
							"recipient": {
								"id": "7326398",
								"name": "TILIFORM INDUSTRIA GRAFICA LTDA",
								"cnpj": "54842406001465",
								"cpf": "",
								"inscricaoEstadual": "209362477112"
							},
							"payer": {
								"id": "7326398",
								"name": "TILIFORM INDUSTRIA GRAFICA LTDA",
								"cnpj": "54842406001465",
								"cpf": ""
							},
							"total": 60.39,
							"productsValue": 500.0,
							"realWeight": 2.0,
							"cubagesCubedWeight": 0.84,
							"originCity": {
								"name": "São Paulo",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Bauru",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "5352",
								"calculationBasis": 60.39,
								"taxRate": 12.0,
								"taxValue": 7.25,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19004566",
						"series": 3,
						"number": 1,
						"key": "41240860960473000596570030000000011348616932",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T14:31:52-03:00",
						"issuedAt": "2024-08-01T14:31:52-03:00",
						"corporationId": 385131,
						"freight": {
							"id": 25177295,
							"corporation": {
								"nickname": "CAS - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000596"
							},
							"sender": {
								"id": "7299095",
								"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000596",
								"cpf": "",
								"inscricaoEstadual": "9034432195"
							},
							"recipient": {
								"id": "7352680",
								"name": "CORBION PRODUTOS RENOVAVEIS LTDA",
								"cnpj": "13190609000546",
								"cpf": null,
								"inscricaoEstadual": "9083020790"
							},
							"payer": {
								"id": "7352680",
								"name": "CORBION PRODUTOS RENOVAVEIS LTDA",
								"cnpj": "13190609000546",
								"cpf": null
							},
							"total": 3607.38,
							"productsValue": 399758.56,
							"realWeight": 25255.32,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Ponta Grossa",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"destinationCity": {
								"name": "Araucária",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"fiscalDetail": {
								"cstType": "40",
								"cfopCode": "5352",
								"calculationBasis": 3607.38,
								"taxRate": 0.0,
								"taxValue": 0.0,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19014154",
						"series": 3,
						"number": 11,
						"key": "35240860960473000243570030000000111592679680",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T18:21:51-03:00",
						"issuedAt": "2024-08-01T18:21:51-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 25174257,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "8018392",
								"name": "TASCO LTDA",
								"cnpj": "43071109000122",
								"cpf": "",
								"inscricaoEstadual": "219004918112"
							},
							"recipient": {
								"id": "7322097",
								"name": "GALI LTDA",
								"cnpj": "13494052000103",
								"cpf": "",
								"inscricaoEstadual": "209285073111"
							},
							"payer": {
								"id": "7322097",
								"name": "GALI LTDA",
								"cnpj": "13494052000103",
								"cpf": ""
							},
							"total": 617.35,
							"productsValue": 95202.46,
							"realWeight": 150.51,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Osasco",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Bauru",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "5352",
								"calculationBasis": 617.35,
								"taxRate": 12.0,
								"taxValue": 74.08,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19018518",
						"series": 3,
						"number": 61,
						"key": "35240860960473001134570030000000611397146613",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T20:07:41-03:00",
						"issuedAt": "2024-08-01T20:07:41-03:00",
						"corporationId": 385128,
						"freight": {
							"id": 25195441,
							"corporation": {
								"nickname": "AGU - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473001134"
							},
							"sender": {
								"id": "7355971",
								"name": "CAMINERO IND.E COM.DE OLEOS LTDA",
								"cnpj": "00474698000187",
								"cpf": null,
								"inscricaoEstadual": "201020665112"
							},
							"recipient": {
								"id": "7464448",
								"name": "AMONEX DO BRASIL INDUSTRIA E COMERCIO LTDA",
								"cnpj": "43165042000195",
								"cpf": null,
								"inscricaoEstadual": "398087246116"
							},
							"payer": {
								"id": "7355971",
								"name": "CAMINERO IND.E COM.DE OLEOS LTDA",
								"cnpj": "00474698000187",
								"cpf": null
							},
							"total": 88.92,
							"productsValue": 1200.0,
							"realWeight": 80.0,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Bariri",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Jandira",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "5352",
								"calculationBasis": 88.92,
								"taxRate": 12.0,
								"taxValue": 10.67,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19014790",
						"series": 3,
						"number": 19,
						"key": "35240860960473000758570030000000191010654923",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T18:30:57-03:00",
						"issuedAt": "2024-08-01T18:30:57-03:00",
						"corporationId": 385217,
						"freight": {
							"id": 25189252,
							"corporation": {
								"nickname": "CPQ - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000758"
							},
							"sender": {
								"id": "7335818",
								"name": "FRIGELAR COMERCIO E INDUSTRIA LTDA",
								"cnpj": "92660406004297",
								"cpf": null,
								"inscricaoEstadual": "122969219112"
							},
							"recipient": {
								"id": "8390695",
								"name": "MCDONALD'S - ARCOS DOURADOS COMERCIO DE ALIMENTOS SA",
								"cnpj": "42591651185682",
								"cpf": "",
								"inscricaoEstadual": "140891750113"
							},
							"payer": {
								"id": "7335818",
								"name": "FRIGELAR COMERCIO E INDUSTRIA LTDA",
								"cnpj": "92660406004297",
								"cpf": null
							},
							"total": 102.78,
							"productsValue": 708.19,
							"realWeight": 3.0,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Campinas",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "São Paulo",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "5353",
								"calculationBasis": 102.78,
								"taxRate": 12.0,
								"taxValue": 12.33,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19014145",
						"series": 3,
						"number": 11,
						"key": "35240860960473000758570030000000111418289212",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-01T18:21:23-03:00",
						"issuedAt": "2024-08-01T18:21:23-03:00",
						"corporationId": 385217,
						"freight": {
							"id": 25191122,
							"corporation": {
								"nickname": "CPQ - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000758"
							},
							"sender": {
								"id": "7347978",
								"name": "DM INDUSTRIAL E COMERCIAL LTDA",
								"cnpj": "02948769000161",
								"cpf": null,
								"inscricaoEstadual": "388012838117"
							},
							"recipient": {
								"id": "7368300",
								"name": "MIX CERTO DISTRIBUIDORA DE COSMETICOS ALIMENTOS E LIMPEZA LT",
								"cnpj": "14741172000120",
								"cpf": null,
								"inscricaoEstadual": "79563021"
							},
							"payer": {
								"id": "7347978",
								"name": "DM INDUSTRIAL E COMERCIAL LTDA",
								"cnpj": "02948769000161",
								"cpf": null
							},
							"total": 790.68,
							"productsValue": 31107.21,
							"realWeight": 436.6,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Itupeva",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Duque de Caxias",
								"state": {
									"name": "Rio de Janeiro",
									"code": "RJ"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6352",
								"calculationBasis": 790.68,
								"taxRate": 12.0,
								"taxValue": 94.88,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19507241",
						"series": 3,
						"number": 3242,
						"key": "35240860960473000243570030000032421067592683",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-08-22T14:19:41-03:00",
						"issuedAt": "2024-08-22T14:19:42-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 25812722,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "7915802",
								"name": "G.M.G Comercio e Servicos de Manute e Reparo de Equipamentos",
								"cnpj": "12382211000115",
								"cpf": null,
								"inscricaoEstadual": "626083831118"
							},
							"recipient": {
								"id": "7307673",
								"name": "KEMIRA CHEMICALS BRASIL LTDA",
								"cnpj": "03944724000262",
								"cpf": "",
								"inscricaoEstadual": "9023157214"
							},
							"payer": {
								"id": "7307673",
								"name": "KEMIRA CHEMICALS BRASIL LTDA",
								"cnpj": "03944724000262",
								"cpf": ""
							},
							"total": 376.15,
							"productsValue": 62372.76,
							"realWeight": 15.0,
							"cubagesCubedWeight": 29.0784,
							"originCity": {
								"name": "Santo André",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Telêmaco Borba",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6352",
								"calculationBasis": 376.15,
								"taxRate": 12.0,
								"taxValue": 45.14,
								"pisRate": 1.65,
								"pisValue": 5.46,
								"cofinsRate": 7.6,
								"cofinsValue": 25.16,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "20222783",
						"series": 3,
						"number": 4178,
						"key": "35240960960473001134570030000041781358504665",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-09-20T16:01:46-03:00",
						"issuedAt": "2024-09-20T16:01:52-03:00",
						"corporationId": 385128,
						"freight": {
							"id": 26748199,
							"corporation": {
								"nickname": "AGU - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473001134"
							},
							"sender": {
								"id": "7308596",
								"name": "PIZATTO & CIA LTDA",
								"cnpj": "44519650000113",
								"cpf": "",
								"inscricaoEstadual": "289002783115"
							},
							"recipient": {
								"id": "8633528",
								"name": "Rodrigo De Oliveira",
								"cnpj": null,
								"cpf": "10819270709",
								"inscricaoEstadual": null
							},
							"payer": {
								"id": "7308596",
								"name": "PIZATTO & CIA LTDA",
								"cnpj": "44519650000113",
								"cpf": ""
							},
							"total": 90.17,
							"productsValue": 399.0,
							"realWeight": 30.0,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Dois Córregos",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Duque de Caxias",
								"state": {
									"name": "Rio de Janeiro",
									"code": "RJ"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6352",
								"calculationBasis": 90.17,
								"taxRate": 12.0,
								"taxValue": 10.82,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "21060812",
						"series": 3,
						"number": 7650,
						"key": "41241060960473000677570030000076501192225465",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-10-24T12:39:50-03:00",
						"issuedAt": "2024-10-24T12:39:51-03:00",
						"corporationId": 385133,
						"freight": {
							"id": 27838331,
							"corporation": {
								"nickname": "CWB - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000677"
							},
							"sender": {
								"id": "7393431",
								"name": "BUSCHLE & LEPPER S.A. - CURITIBA",
								"cnpj": "84684471001802",
								"cpf": null,
								"inscricaoEstadual": "1018052047"
							},
							"recipient": {
								"id": "7324714",
								"name": "CONTITECH DO BRASIL PRODUTOS AUTOMOTIVOS E INDUSTR",
								"cnpj": "08832031001354",
								"cpf": "",
								"inscricaoEstadual": "9082566705"
							},
							"payer": {
								"id": "7393431",
								"name": "BUSCHLE & LEPPER S.A. - CURITIBA",
								"cnpj": "84684471001802",
								"cpf": null
							},
							"total": 334.68,
							"productsValue": 8004.0,
							"realWeight": 601.04,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Curitiba",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"destinationCity": {
								"name": "Ponta Grossa",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"fiscalDetail": {
								"cstType": "40",
								"cfopCode": "5353",
								"calculationBasis": 286.16,
								"taxRate": 0.0,
								"taxValue": 0.0,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "19975652",
						"series": 3,
						"number": 5540,
						"key": "41240960960473000596570030000055401302241423",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-09-11T17:52:41-03:00",
						"issuedAt": "2024-09-11T17:52:41-03:00",
						"corporationId": 385131,
						"freight": {
							"id": 26448524,
							"corporation": {
								"nickname": "CAS - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000596"
							},
							"sender": {
								"id": "7336591",
								"name": "TIGRE FERRAMENTAS PARA CONSTRUCAO CIVIL S.A.",
								"cnpj": "33064262000250",
								"cpf": null,
								"inscricaoEstadual": "9103478291"
							},
							"recipient": {
								"id": "7299095",
								"name": "RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000596",
								"cpf": "",
								"inscricaoEstadual": "9034432195"
							},
							"payer": {
								"id": "7336591",
								"name": "TIGRE FERRAMENTAS PARA CONSTRUCAO CIVIL S.A.",
								"cnpj": "33064262000250",
								"cpf": null
							},
							"total": 864.36,
							"productsValue": 28764.27,
							"realWeight": 248.72,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Castro",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"destinationCity": {
								"name": "Piraquara",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"fiscalDetail": {
								"cstType": "40",
								"cfopCode": "5352",
								"calculationBasis": 864.36,
								"taxRate": 0.0,
								"taxValue": 0.0,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "20507381",
						"series": 3,
						"number": 978,
						"key": "26241060960473000839570030000009781803406580",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-10-02T17:27:58-03:00",
						"issuedAt": "2024-10-02T17:27:58-03:00",
						"corporationId": 385227,
						"freight": {
							"id": 27124430,
							"corporation": {
								"nickname": "REC - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000839"
							},
							"sender": {
								"id": "7371368",
								"name": "BBF DISTRIBUIDORA NE LTDA",
								"cnpj": "40938967000133",
								"cpf": null,
								"inscricaoEstadual": "094618330"
							},
							"recipient": {
								"id": "7388668",
								"name": "RAYTON PINTURA E CONSTRUCAO EIRELI ME",
								"cnpj": "20356545000142",
								"cpf": null,
								"inscricaoEstadual": "701078269116"
							},
							"payer": {
								"id": "7371368",
								"name": "BBF DISTRIBUIDORA NE LTDA",
								"cnpj": "40938967000133",
								"cpf": null
							},
							"total": 128.47,
							"productsValue": 1613.76,
							"realWeight": 69.45,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "São Lourenço da Mata",
								"state": {
									"name": "Pernambuco",
									"code": "PE"
								}
							},
							"destinationCity": {
								"name": "Sumaré",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6353",
								"calculationBasis": 128.47,
								"taxRate": 12.0,
								"taxValue": 15.42,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				},
				{
					"node": {
						"id": "20507011",
						"series": 3,
						"number": 9357,
						"key": "35241060960473000243570030000093571120096419",
						"emissionType": "normal",
						"status": "authorized",
						"createdAt": "2024-10-02T17:20:23-03:00",
						"issuedAt": "2024-10-02T17:20:23-03:00",
						"corporationId": 385129,
						"freight": {
							"id": 27125190,
							"corporation": {
								"nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
								"cnpj": "60960473000243"
							},
							"sender": {
								"id": "8454864",
								"name": "BIZERBA DO BRASIL LTDA",
								"cnpj": "04409332000185",
								"cpf": "",
								"inscricaoEstadual": "206412775116"
							},
							"recipient": {
								"id": "7353813",
								"name": "FRISIA COOPERATIVA AGROINDUSTRIAL",
								"cnpj": "76107770002224",
								"cpf": null,
								"inscricaoEstadual": "9053097305"
							},
							"payer": {
								"id": "7353813",
								"name": "FRISIA COOPERATIVA AGROINDUSTRIAL",
								"cnpj": "76107770002224",
								"cpf": null
							},
							"total": 145.8,
							"productsValue": 6565.68,
							"realWeight": 72.0,
							"cubagesCubedWeight": 0.0,
							"originCity": {
								"name": "Barueri",
								"state": {
									"name": "São Paulo",
									"code": "SP"
								}
							},
							"destinationCity": {
								"name": "Ponta Grossa",
								"state": {
									"name": "Paraná",
									"code": "PR"
								}
							},
							"fiscalDetail": {
								"cstType": "00",
								"cfopCode": "6353",
								"calculationBasis": 145.8,
								"taxRate": 12.0,
								"taxValue": 17.5,
								"pisRate": 0.0,
								"pisValue": 0.0,
								"cofinsRate": 0.0,
								"cofinsValue": 0.0,
								"hasDifal": false,
								"difalTaxValueOrigin": 0.0,
								"difalTaxValueDestination": 0.0
							}
						}
					}
				}
			]
		}
	}
}