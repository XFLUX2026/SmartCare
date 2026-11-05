# SMART CARE (Etapa 1)

Central de diagnóstico e cuidados do celular Android. Kotlin + Jetpack Compose.
Esta etapa traz o Dashboard com dados reais: armazenamento, RAM, bateria e nota de saúde (0 a 100) com critérios visíveis.
As abas Limpeza, Bateria, Monitor e Arquivos são marcadores para as próximas etapas.

## Estrutura
- `app/src/main/java/com/smartcare/app/Metrics.kt`: leitura dos indicadores e cálculo da nota
- `app/src/main/java/com/smartcare/app/MainActivity.kt`: interface (Compose)
- `app/src/main/res/`: ícone adaptativo e cores
- `.github/workflows/build.yml`: compilação automática do APK

## Requisitos
- Android mínimo: 8.0 (API 26). Compilação com compileSdk 34
- JDK 17, Gradle 8.9, Android Gradle Plugin 8.5.2, Kotlin 2.0.20
- Permissões: nenhuma nesta etapa

## Gerar o APK pelo GitHub Actions (recomendado)
1. Crie um repositório no GitHub e envie o conteúdo desta pasta (a pasta `.github` precisa ir junto).
2. Abra a aba **Actions**, entre na execução **Build APK** e aguarde ficar verde.
3. Em **Artifacts**, baixe **SmartCare-APK** e extraia o `app-release.apk`.
4. Copie para o celular e instale (permita "instalar apps de fontes desconhecidas" para o navegador ou gerenciador de arquivos).

Observação: a chave de assinatura é criada a cada execução. Para atualizar um app já instalado sem desinstalar, use uma chave fixa (guardada em GitHub Secrets).

## Gerar o APK no Android Studio
1. **File → Open** e escolha esta pasta. Se pedir, aceite o Gradle 8.9 e o JDK 17.
2. Aguarde o sync terminar.
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. O arquivo fica em `app/build/outputs/apk/`.

Pela linha de comando (com Gradle 8.9 instalado): `gradle assembleRelease`.
Para criar o wrapper do projeto: `gradle wrapper --gradle-version 8.9`.

## Princípios do projeto
Leituras pontuais (sem serviço permanente), sem números fictícios, sem "liberar RAM" falso, sem exclusões sem confirmação.
