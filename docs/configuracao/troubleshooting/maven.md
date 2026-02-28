# Maven - Build Reprodutivel e Sem Surpresas

## Objetivo
Padronizar a execucao do Maven em ambiente local e CI com:
- cache de dependencias e plugins
- suporte a mirror corporativo
- validacao rapida de problemas de configuracao

## Execucao local recomendada

No Windows, execute Maven pela raiz do projeto:

```bat
mvn -version
mvn -B -ntp -DskipTests dependency:go-offline
mvn -B -ntp test
```

Observacao:
- o projeto possui `mvn.bat` na raiz para reduzir problemas de `JAVA_HOME`.

## Erro de rede (403/5xx) ao baixar plugin/dependencia

Quando ocorrer erro de resolucao no Maven Central:
1. valide conectividade/restricoes de proxy
2. rode warmup local (`dependency:go-offline`)
3. configure mirror corporativo (recomendado)

## Configurar mirror corporativo local

1. Copie o template:

```bat
copy .ci\maven-settings-template.xml %USERPROFILE%\.m2\settings.xml
```

2. Edite `%USERPROFILE%\.m2\settings.xml` com URL e credenciais do seu mirror.

3. Revalide:

```bat
mvn -B -ntp -s %USERPROFILE%\.m2\settings.xml -DskipTests dependency:go-offline
mvn -B -ntp -s %USERPROFILE%\.m2\settings.xml test
```

## CI (GitHub Actions)

O workflow CI ja esta preparado para:
- `cache: maven` (actions/setup-java)
- warmup de dependencias/plugins (`dependency:go-offline`)
- uso opcional de mirror via secrets:
  - `MAVEN_MIRROR_URL`
  - `MAVEN_MIRROR_USERNAME`
  - `MAVEN_MIRROR_PASSWORD`

Sem esses secrets, o pipeline usa Maven Central normalmente.

## Checklist rapido de diagnostico

```bat
mvn -version
mvn -B -ntp help:effective-settings
mvn -B -ntp -DskipTests dependency:go-offline
mvn -B -ntp test
```

Se o `effective-settings` nao mostrar o mirror esperado, a configuracao nao foi aplicada.
