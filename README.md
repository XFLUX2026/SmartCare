# SMART CARE 0.4 — XFLUX

Aplicativo Android de diagnóstico, monitoramento de bateria, organização de mídias e informações do aparelho. Desenvolvido em Kotlin + Jetpack Compose.

## Recursos

### Home Care
- Painel com saúde geral, bateria, RAM e armazenamento.
- Indicadores reorganizáveis e ocultáveis, com preferência salva no aparelho.
- Atualização manual e atualização periódica enquanto a tela inicial está aberta.
- Widget com quatro indicadores, botão de atualização e atualização ao conectar/desconectar o carregador.

### Três temas
- Azul clássico.
- Azul-petróleo.
- Black, com fundo escuro e detalhes luminosos.
- A escolha fica salva e também altera as cores do widget.

### Diagnóstico
- Nota de referência de 0 a 100, baseada em critérios visíveis de armazenamento, memória e temperatura.
- Não é certificação oficial nem substitui diagnósticos do fabricante.

### Limpeza inteligente
- Localiza arquivos de mídia grandes.
- Identifica possíveis duplicados e compara conteúdo por SHA-256 quando possível.
- Sinaliza imagens possivelmente desfocadas por análise aproximada.
- Mostra nome, tipo e tamanho dos itens para revisão.
- Nenhum arquivo é apagado automaticamente: a seleção e a confirmação ficam com a pessoa usuária.
- O Android restringe a limpeza do cache privado de outros aplicativos. O Smart Care encaminha para as configurações do sistema em vez de prometer uma limpeza que o Android não permite.

### Bateria
- Percentual, estado, temperatura e voltagem quando disponíveis.
- Corrente elétrica e estimativa de tempo até a carga completa, quando o fabricante fornece os dados necessários.
- Atualização periódica e atualização imediata ao conectar/desconectar o carregador enquanto o painel está aberto.

### Tela de carregamento
- Proteção de tela nativa do Android (DreamService).
- Relógio grande, data, saudação, círculo animado com percentual, temperatura e corrente quando disponível.
- Mantém a tela de bloqueio, PIN, senha e biometria nativos do Android.
- Para ativar: abra as configurações Android de **Proteção de tela / Protetor de tela**, selecione **Smart Care** e configure para iniciar durante o carregamento. O nome e o caminho podem variar conforme o fabricante.

## Limitações importantes
- O Android não permite que aplicativos comuns apaguem silenciosamente o cache privado de outros aplicativos.
- A análise de desfoque é heurística; uma foto artística, escura ou com pouco detalhe pode ser sinalizada incorretamente.
- A análise de duplicados compara arquivos com mesmo nome e tamanho; quando a leitura permite, o SHA-256 confirma se o conteúdo é idêntico.
- Corrente, voltagem e tempo estimado dependem dos sensores e da implementação de cada fabricante.
- A tela de carregamento depende de o usuário ativar Smart Care como proteção de tela nas configurações do Android. O sistema pode controlar brilho e comportamento durante o repouso.

## Requisitos técnicos
- Android mínimo: 8.0 (API 26).
- compileSdk / targetSdk: 34.
- JDK 17 e Gradle 8.9.
- Kotlin + Jetpack Compose.

## Compilação
O workflow `.github/workflows/android-build.yml` prepara APKs Debug e Release e executa lint. Os artefatos são publicados na execução do GitHub Actions com o nome `smartcare-v0.4-apks`.

**Observação de assinatura:** sem uma chave de produção configurada em GitHub Secrets, o APK Release é assinado com a chave de depuração. Essa chave pode mudar entre execuções; nesse caso, para instalar uma nova compilação sobre uma anterior, talvez seja necessário desinstalar a versão antiga primeiro. Uma versão de distribuição permanente deve usar uma chave de assinatura fixa e protegida.

## Princípios do projeto
- Sem exclusão automática de arquivos.
- Sem promessas falsas de “liberar RAM” ou limpar dados protegidos pelo Android.
- Sem interceptar ou substituir a autenticação da tela de bloqueio.
- Preferência por informações reais e indicação explícita quando um dado não está disponível.
