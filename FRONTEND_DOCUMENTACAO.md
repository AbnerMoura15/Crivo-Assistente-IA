# Documentação do Frontend — Crivo AI

> **Para quem é este documento?**  
> Desenvolvedores que conhecem bem o backend Java e querem entender como o frontend
> foi construído, quais tecnologias foram usadas e por que as decisões foram tomadas
> dessa forma. Cada seção parte do zero e não assume conhecimento prévio de frontend.

---

## Sumário

1. [A Grande Decisão: Sem Framework](#1-a-grande-decisão-sem-framework)
2. [Como o Arquivo Chega ao Navegador](#2-como-o-arquivo-chega-ao-navegador)
3. [CSS — Variáveis, Temas e Cores](#3-css--variáveis-temas-e-cores)
4. [Layout — Flexbox e Grid](#4-layout--flexbox-e-grid)
5. [Versão Mobile — Media Queries e Hamburger Menu](#5-versão-mobile--media-queries-e-hamburger-menu)
6. [SPA — Single Page Application sem React](#6-spa--single-page-application-sem-react)
7. [Autenticação no Frontend — JWT no Navegador](#7-autenticação-no-frontend--jwt-no-navegador)
8. [Fetch API — Chamando o Backend](#8-fetch-api--chamando-o-backend)
9. [A Interface de Chat — Cada Detalhe](#9-a-interface-de-chat--cada-detalhe)
10. [Markdown no Navegador — parseMarkdown()](#10-markdown-no-navegador--parsemarkdown)
11. [Upload de Arquivo com Drag and Drop](#11-upload-de-arquivo-com-drag-and-drop)
12. [Polling — Verificando Status em Loop](#12-polling--verificando-status-em-loop)
13. [Dashboard de Qualidade RAG](#13-dashboard-de-qualidade-rag)
14. [Painel de Usuários](#14-painel-de-usuários)
15. [Log de Auditoria](#15-log-de-auditoria)
16. [Config IA — Sliders em Tempo Real](#16-config-ia--sliders-em-tempo-real)
17. [Animações CSS](#17-animações-css)
18. [Toast — Sistema de Notificações](#18-toast--sistema-de-notificações)
19. [Modais](#19-modais)
20. [Segurança no Frontend](#20-segurança-no-frontend)

---

## 1. A Grande Decisão: Sem Framework

### O que são frameworks de frontend?

Quem trabalha com frontend moderno provavelmente conhece React, Vue.js ou Angular.
São bibliotecas/frameworks que facilitam a criação de interfaces complexas.

O frontend do Crivo AI usa **zero frameworks externos**. Apenas:

| Tecnologia | Versão | O que é |
|------------|--------|---------|
| **HTML5** | Padrão W3C | Estrutura das páginas |
| **CSS3** | Padrão W3C | Estilo e layout |
| **JavaScript** | ES2022 (vanilla) | Lógica e interatividade |

Isso significa que o arquivo `index.html` é **autossuficiente** — não depende de nenhum
pacote npm, nenhum `node_modules`, nenhum processo de build.

### Por que essa escolha?

**1. Zero dependências externas**  
Frameworks populares mudam rápido. React 16 → 17 → 18 → 19 traz breaking changes.
Um frontend vanilla não quebra com o tempo.

**2. Um único arquivo**  
Todo o frontend cabe em um único `index.html` com ~1.900 linhas. O Spring Boot serve
esse arquivo como recurso estático — sem servidor Node.js, sem build step, sem CDN.

**3. Perfeito para aplicações internas**  
O Crivo AI é uma ferramenta corporativa interna, não uma loja com milhões de páginas.
Para isso, vanilla JS é mais que suficiente e tem menos superfície de ataque de segurança.

**4. Performance**  
Um framework React mínimo pesa ~130 KB comprimido. Este frontend inteiro pesa ~60 KB.

### O que um framework resolveria que foi feito na mão?

| Problema | Framework | Solução usada no projeto |
|----------|-----------|--------------------------|
| Atualizar a tela sem recarregar | React state | `innerHTML` + DOM manipulation |
| Roteamento entre páginas | React Router | `showView()` function |
| Componentes reutilizáveis | React components | Template strings em funções JS |
| Reatividade (estado muda → tela atualiza) | useState hook | Funções que recarregam dados |

---

## 2. Como o Arquivo Chega ao Navegador

### O Spring Boot como servidor de arquivos estáticos

Qualquer arquivo em `src/main/resources/static/` é servido automaticamente pelo Spring Boot
como se fosse um servidor de arquivos comum.

```
Navegador pede: GET http://localhost:8080/
Spring Boot serve: src/main/resources/static/index.html
```

Isso é configurado automaticamente pelo `spring-boot-starter-web` —
sem nenhuma linha de código extra no Java.

**Referência:** [Spring Boot Static Content](https://docs.spring.io/spring-boot/reference/web/servlet.html#web.servlet.spring-mvc.static-content)

### A tag `<meta name="viewport">`

```html
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
```

Esta linha é **essencial** para que o site funcione bem em celular. Sem ela, o navegador
mobile assume que a página foi feita para desktop e a encolhe para caber na tela,
tornando o texto minúsculo.

`width=device-width` → usa a largura real do dispositivo  
`initial-scale=1.0` → sem zoom inicial

### `100dvh` em vez de `100vh`

```css
body { height: 100dvh; }
```

`vh` = viewport height (altura da janela)  
`dvh` = dynamic viewport height

Em celulares, a barra de endereço do Chrome/Safari aparece e desaparece ao rolar.
Com `100vh`, a barra de endereços "come" parte do conteúdo. Com `100dvh`, a altura
se ajusta dinamicamente — o app sempre preenche exatamente a tela visível.

---

## 3. CSS — Variáveis, Temas e Cores

### CSS Custom Properties (Variáveis CSS)

```css
:root {
  --bg:       #0f1117;   /* fundo da página */
  --surface:  #171b26;   /* fundo da sidebar e topbar */
  --surface2: #1e2334;   /* fundo de cards e inputs */
  --border:   #2a3050;   /* cor de bordas */
  --accent:   #4f7cff;   /* azul — cor principal, botões */
  --accent2:  #7b5ea7;   /* roxo — gradientes */
  --green:    #3ecf8e;   /* verde — sucesso, status ativo */
  --red:      #ff6b6b;   /* vermelho — erro, alerta */
  --yellow:   #f6c90e;   /* amarelo — aviso, processando */
  --text:     #e2e8f0;   /* texto principal */
  --muted:    #6b7a99;   /* texto secundário, labels */
}
```

**O que são CSS Custom Properties?**  
São variáveis no CSS. Você define uma vez em `:root` (o elemento raiz — equivalente ao `<html>`)
e usa em qualquer lugar com `var(--nome)`. Se precisar mudar a cor primária, muda em um lugar
e afeta o site inteiro.

**Analogia Java:** É como uma constante `static final String COR_PRINCIPAL = "#4f7cff"` que
é referenciada em todo o código.

**Referência:** [CSS Custom Properties - MDN](https://developer.mozilla.org/en-US/docs/Web/CSS/Using_CSS_custom_properties)

### Modo Escuro / Claro — Troca de Tema

```css
/* Modo escuro (padrão) — definido em :root */
:root {
  --bg: #0f1117;
  --text: #e2e8f0;
  /* ... */
}

/* Modo claro — sobrescreve apenas quando a classe 'light' está no <html> */
:root.light {
  --bg: #f0f2f7;
  --text: #1a1e2e;
  /* ... */
}
```

```javascript
// JavaScript que aplica/remove o tema
function applyTheme(light) {
  if (light) {
    document.documentElement.classList.add('light');    // adiciona classe no <html>
  } else {
    document.documentElement.classList.remove('light'); // remove classe do <html>
  }
}

// Salva preferência no localStorage (persiste entre sessões)
function toggleTheme() {
  const isLight = document.documentElement.classList.contains('light');
  const next = !isLight;
  localStorage.setItem('ragbot_theme', next ? 'light' : 'dark');
  applyTheme(next);
}

// Aplica imediatamente ao carregar (antes mesmo do login aparecer)
applyTheme(localStorage.getItem('ragbot_theme') === 'light');
```

**`localStorage`** — armazenamento permanente no navegador. Diferente de `sessionStorage`
(que perde ao fechar a aba), o `localStorage` persiste até o usuário limpar os dados.
Aqui é usado para lembrar o tema escolhido.

**`document.documentElement`** — é o elemento `<html>`. Adicionar `.light` nele faz com
que o CSS `:root.light { ... }` seja ativado, trocando todas as variáveis de cor.

### Gradientes

```css
.logo-icon {
  background: linear-gradient(135deg, var(--accent), var(--accent2));
  /* azul #4f7cff → roxo #7b5ea7, diagonal de 135° */
}
```

`linear-gradient(135deg, cor1, cor2)` — gradiente linear diagonal.
O `135deg` define o ângulo: 0° = de baixo para cima, 90° = da esquerda para direita,
135° = diagonal superior-direita para inferior-esquerda.

---

## 4. Layout — Flexbox e Grid

### Flexbox — O Layout Principal

```css
body {
  display: flex;        /* ativa o Flexbox */
  flex-direction: row;  /* filhos lado a lado (padrão) */
}

.sidebar {
  width: 260px;
  min-width: 260px;     /* nunca encolhe */
}

.main {
  flex: 1;              /* ocupa TODO o espaço restante */
}
```

**O que é Flexbox?**  
É um sistema de layout CSS que distribui elementos em uma linha ou coluna.
Pense nele como uma `HBox` ou `VBox` do JavaFX, ou como um `FlowLayout`/`BorderLayout` do Swing.

```
┌──────────────────────────────────────────────────────────┐
│ body (display:flex, flex-direction:row)                  │
│                                                          │
│  ┌──────────┐  ┌───────────────────────────────────────┐ │
│  │ sidebar  │  │ main (flex:1 → ocupa o resto)         │ │
│  │ 260px    │  │                                       │ │
│  │          │  │                                       │ │
│  └──────────┘  └───────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

**`flex: 1`** — significa "ocupe todo o espaço disponível". Se a sidebar tem 260px
e a tela tem 1200px, o `main` recebe 940px automaticamente.

**Referência:** [Flexbox - MDN](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_flexible_box_layout)

### CSS Grid — O Dashboard de Métricas

```css
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
}
```

**O que é CSS Grid?**  
Sistema de layout bidimensional (linhas E colunas). Enquanto Flexbox organiza
em uma dimensão (linha OU coluna), Grid organiza em duas.

**`repeat(auto-fit, minmax(160px, 1fr))`** — esta é a parte inteligente:
- `auto-fit` → cria quantas colunas couberem
- `minmax(160px, 1fr)` → cada coluna tem mínimo 160px e máximo 1 fração do espaço

**Resultado visual por tamanho de tela:**

```
Tela grande (1200px):     [Card][Card][Card][Card]  ← 4 colunas
Tela média (600px):       [Card][Card][Card]         ← 3 colunas
Tela pequena (480px):     [Card][Card]               ← 2 colunas
Celular (320px):          [Card]                     ← 1 coluna
```

Tudo isso **sem nenhuma linha de JavaScript** — o CSS se ajusta automaticamente.

**Referência:** [CSS Grid - MDN](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_grid_layout)

---

## 5. Versão Mobile — Media Queries e Hamburger Menu

### O que são Media Queries?

Media queries permitem aplicar CSS diferente dependendo do tamanho da tela.
É como um `if` dentro do CSS:

```css
/* CSS normal — para todas as telas */
.sidebar { width: 260px; }

/* CSS para telas até 768px (tablet/mobile) */
@media (max-width: 768px) {
  .hamburger { display: flex; }   /* aparece o botão ☰ */

  .sidebar {
    position: fixed;               /* sai do fluxo normal */
    left: -270px;                  /* começa fora da tela */
    transition: left .25s ease;   /* animação suave ao abrir */
    z-index: 500;                  /* fica na frente de tudo */
  }

  .sidebar.open {
    left: 0;                       /* quando aberto, aparece */
  }

  .main { width: 100%; }          /* ocupa a tela toda */
  .topbar { padding-left: 56px; } /* espaço para o botão ☰ */
}

/* CSS para celulares (até 480px) */
@media (max-width: 480px) {
  .messages { padding: 12px; }    /* menos espaçamento */
  .metrics-grid {
    grid-template-columns: 1fr 1fr; /* força 2 colunas */
  }
  .filter-bar { flex-direction: column; } /* filtros empilhados */
  .input-hint { display: none; }  /* esconde dica "Enter para enviar" */
}
```

**Dois breakpoints:**
- `768px` → tablet e celular — sidebar vira um drawer (gaveta)
- `480px` → celular pequeno — ajustes de espaçamento e layout

### O Hamburger Menu

```html
<!-- O botão ☰ — aparece apenas no mobile -->
<button class="hamburger" id="hamburger-btn" onclick="toggleSidebar()">
  <span></span>  <!-- linha 1 -->
  <span></span>  <!-- linha 2 -->
  <span></span>  <!-- linha 3 -->
</button>
```

```css
.hamburger {
  display: none;                    /* escondido no desktop */
  position: fixed;                  /* fixo no canto superior esquerdo */
  top: 12px; left: 12px;
  z-index: 600;                     /* na frente da sidebar (z-index 500) */
}

/* Animação de ☰ → ✕ quando aberto */
.hamburger.open span:nth-child(1) { transform: translateY(7px) rotate(45deg); }
.hamburger.open span:nth-child(2) { opacity: 0; }
.hamburger.open span:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }
```

As três linhas (`<span>`) do ☰ se animam para formar um ✕ quando o menu está aberto.
Isso usa `transform: rotate()` — rotação CSS pura, sem JavaScript.

```javascript
function toggleSidebar() {
  const sidebar = document.getElementById('sidebar');
  const backdrop = document.getElementById('sidebar-backdrop');
  const btn = document.getElementById('hamburger-btn');

  const isOpen = sidebar.classList.toggle('open');  // adiciona/remove 'open'
  backdrop.classList.toggle('visible', isOpen);      // mostra/esconde backdrop
  btn.classList.toggle('open', isOpen);              // anima ☰ → ✕
}
```

**`classList.toggle('open')`** — se tem a classe, remove; se não tem, adiciona.
Retorna `true` se a classe foi adicionada.

### O Backdrop (fundo escurecido)

```html
<!-- Clicando fora da sidebar, ela fecha -->
<div class="sidebar-backdrop" id="sidebar-backdrop" onclick="closeSidebar()"></div>
```

```css
.sidebar-backdrop {
  display: none;
  position: fixed;
  inset: 0;                          /* cobre tudo: top/right/bottom/left = 0 */
  background: rgba(0,0,0,.45);       /* preto com 45% de opacidade */
  backdrop-filter: blur(1px);        /* desfoca levemente o conteúdo atrás */
  z-index: 499;                      /* entre o conteúdo (0) e a sidebar (500) */
}
.sidebar-backdrop.visible { display: block; }
```

**`inset: 0`** — shorthand moderno para `top: 0; right: 0; bottom: 0; left: 0`.

**`backdrop-filter: blur(1px)`** — desfoca o conteúdo atrás do elemento.
Funciona como o efeito "vidro fosco" de iOS.

### Fechar Sidebar ao Navegar

```javascript
function showView(view) {
  // ...
  // Fecha automaticamente no mobile ao clicar em um item do menu
  if (window.innerWidth <= 768) closeSidebar();
  // ...
}
```

`window.innerWidth` — largura atual da janela em pixels.
Isso garante que em celular, clicar em "Chat" ou "Dashboard" fecha o menu automaticamente.

---

## 6. SPA — Single Page Application sem React

### O que é uma SPA?

Uma aplicação de página única (SPA) carrega **uma vez** e troca o conteúdo visível
sem recarregar a página. É o comportamento do Gmail, Google Maps, etc.

No Crivo AI, todas as "páginas" (chat, dashboard, usuários...) são `<div>` que ficam
escondidas ou visíveis dependendo de qual item do menu está ativo.

### Como as Views Funcionam

```html
<!-- Cada view é um <div> com id="view-nome" -->
<div id="view-chat"       style="display:flex">...</div>   <!-- visível -->
<div id="view-dashboard"  style="display:none">...</div>   <!-- escondido -->
<div id="view-users"      style="display:none">...</div>   <!-- escondido -->
<div id="view-audit"      style="display:none">...</div>   <!-- escondido -->
<div id="view-namespaces" style="display:none">...</div>   <!-- escondido -->
<div id="view-config-ia"  style="display:none">...</div>   <!-- escondido -->
```

```javascript
// Lista de todas as views existentes
const ALL_VIEWS = ['chat','history','session','docs','dashboard','users','audit','namespaces','config-ia'];

function showView(view) {
  // 1. Esconde TODAS as views
  ALL_VIEWS.forEach(v => {
    const el = document.getElementById('view-' + v);
    if (el) el.style.display = 'none';

    // Remove o destaque do item de menu
    const nav = document.getElementById('nav-' + v);
    if (nav) nav.classList.remove('active');
  });

  // 2. Mostra apenas a view pedida
  const target = document.getElementById('view-' + view);
  if (target) target.style.display = 'flex';

  // 3. Destaca o item de menu correspondente
  const nav = document.getElementById('nav-' + view);
  if (nav) nav.classList.add('active');

  // 4. Fecha sidebar no mobile
  if (window.innerWidth <= 768) closeSidebar();

  // 5. Carrega dados da view ao abrir
  if (view === 'users')      loadUsers();
  if (view === 'dashboard')  loadDashboard();
  if (view === 'audit')      loadAudit();
  if (view === 'config-ia')  loadRagConfig();
}
```

**`el.style.display = 'none'`** — esconde o elemento (como `setVisible(false)` no Swing).  
**`el.style.display = 'flex'`** — mostra e aplica layout Flexbox.

### Menu Dinâmico por Perfil de Usuário

```javascript
function initApp() {
  // Menus de ADMIN — escondidos para VIEWER e EDITOR
  if (currentUser.role === 'ADMIN') {
    document.getElementById('admin-section').style.display = 'block';
    document.getElementById('nav-dashboard').style.display = 'flex';
    document.getElementById('nav-users').style.display = 'flex';
    document.getElementById('nav-audit').style.display = 'flex';
    document.getElementById('nav-namespaces').style.display = 'flex';
    document.getElementById('nav-config-ia').style.display = 'flex';
    document.getElementById('upload-section').style.display = 'block'; // upload de docs
  } else if (currentUser.role === 'EDITOR') {
    document.getElementById('upload-section').style.display = 'block'; // só upload
  }
  // VIEWER não vê nenhum desses
}
```

O perfil vem do JWT após o login (campo `role`). A tela se adapta sem nova chamada à API.

---

## 7. Autenticação no Frontend — JWT no Navegador

### O Fluxo de Login

```javascript
async function doLogin() {
  const username = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;

  btn.disabled = true; // evita clique duplo

  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });

  const data = await res.json();
  token = data.token;  // guarda o JWT em memória (variável JavaScript)

  // Decodifica o payload do JWT (parte do meio, Base64)
  const payload = JSON.parse(atob(token.split('.')[1]));
  // payload = { sub: "admin", role: "ADMIN", iat: 1234567890, exp: 1234654290 }

  currentUser = { username: payload.sub, role: payload.role };

  // Restaura sessão de chat anterior (se existir)
  currentSessionId = sessionStorage.getItem('ragbot_session_' + currentUser.username) || null;

  document.getElementById('login-screen').style.display = 'none';
  initApp();
}
```

**`atob(token.split('.')[1])`** — decodifica Base64. O JWT tem 3 partes separadas por `.`:
```
eyJhbGci...   →   HEADER   (algoritmo)
eyJzdWIi...   →   PAYLOAD  (dados: username, role, expiração)
SflKxwRJS...  →   SIGNATURE (prova que não foi alterado)
```

`split('.')[1]` pega a parte do meio, `atob()` decodifica Base64 para string JSON.

**Importante:** O frontend só **lê** o payload do JWT — não verifica a assinatura.
A verificação real acontece no backend (`JwtTokenProvider.isTokenValid()`).

### Onde o JWT é Guardado?

```javascript
let token = null;  // variável JavaScript na memória da página
```

O JWT é guardado **em memória** (variável JavaScript), não no `localStorage` nem em cookies.

**Por que em memória e não localStorage?**

| Storage | Acessível por | Risco |
|---------|--------------|-------|
| `localStorage` | JavaScript de qualquer script da página | XSS pode roubar o token |
| `sessionStorage` | JavaScript na mesma aba | XSS pode roubar o token |
| **Memória (variável JS)** | Apenas o código da mesma página | XSS não persiste após reload |
| Cookie `HttpOnly` | Apenas o servidor | Mais seguro — JavaScript não acessa |

A escolha por variável em memória é um bom compromisso para aplicação interna:
se a aba fechar, o token some. Para aumentar a segurança, o ideal seria usar
cookies `HttpOnly` (o backend enviaria o JWT em cookie, não no response body).

### Como o JWT é Enviado em Cada Requisição

```javascript
// Todas as chamadas autenticadas incluem o header Authorization
const res = await fetch('/api/documents', {
  headers: {
    'Authorization': 'Bearer ' + token  // "Bearer eyJhbGci..."
  }
});
```

O formato `Bearer <token>` é o padrão para JWT em APIs REST.
O Spring Security lê este header no `JwtAuthenticationFilter`.

---

## 8. Fetch API — Chamando o Backend

### O que é a Fetch API?

Fetch é a API moderna do JavaScript para fazer requisições HTTP.
É o equivalente frontend do `WebClient` do Spring Boot — assíncrona e baseada em Promises.

**Comparação Java vs JavaScript:**

```java
// Java — Spring WebClient
webClient.post()
  .uri("/api/chat")
  .bodyValue(requestDto)
  .retrieve()
  .bodyToMono(ChatResponseDto.class)
  .block();
```

```javascript
// JavaScript — Fetch API
const res = await fetch('/api/chat', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer ' + token,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ question, sessionId, namespaceId })
});
const data = await res.json();
```

### `async/await` — Código Assíncrono Legível

```javascript
async function sendMessage() {          // função assíncrona
  const res = await fetch('/api/chat', {...});  // aguarda a resposta HTTP
  const data = await res.json();                // aguarda o parse do JSON
  appendMsg('bot', data.answer);               // usa o resultado
}
```

**`async function`** — declara uma função assíncrona (retorna uma `Promise`).  
**`await`** — pausa a execução **desta função** até a Promise resolver.
O restante do navegador continua funcionando — não trava a tela.

**Analogia Java:** É como usar `.get()` em um `Future`, mas sem bloquear a thread principal.

### Tratamento de Erros HTTP

```javascript
const res = await fetch('/api/chat', {...});
const data = await res.json();

if (res.status === 429) {
  // Distingue limite de tokens do usuário vs rate limit da Groq
  const isTokenLimit = data.error === 'TOKEN_LIMIT_EXCEEDED';
  const msg = isTokenLimit
    ? 'Você atingiu o limite do plano. Fale com o administrador.'
    : 'Limite da IA atingido. Aguarde alguns segundos.';
  appendSystemMsg(isTokenLimit ? '🚫' : '⏳', msg);

  // IMPORTANTE: restaura o texto que o usuário digitou
  textarea.value = savedQuestion;
} else if (!res.ok) {
  appendSystemMsg('⚠️', data.message || 'Ocorreu um erro.');
}
```

**`res.ok`** — `true` se o status HTTP for 200-299.  
**`res.status`** — o código HTTP numérico (200, 404, 429, 500...).

**Restaurar o texto:** Se a requisição falhar, o usuário não perde o que digitou.
O texto é salvo em `savedQuestion` antes do `textarea.value = ''`.

---

## 9. A Interface de Chat — Cada Detalhe

### Renderizando Mensagens

```javascript
function appendMsg(role, content, interactionId, sources) {
  const msgs = document.getElementById('messages');
  const isUser = role === 'user';
  const div = document.createElement('div');
  div.className = 'msg ' + role;  // 'msg user' ou 'msg bot'

  // Mensagens do usuário: escapa HTML para segurança (ver seção 20)
  // Mensagens da IA: renderiza Markdown
  const bubbleContent = isUser
    ? escHtml(content).replace(/\n/g, '<br/>')
    : parseMarkdown(content);

  // Source chips — arquivos de origem da resposta
  const chipsHtml = (!isUser && sources && sources.length)
    ? `<div class="source-chips">
         ${sources.map(s => `<span class="source-chip">📎 ${escHtml(s)}</span>`).join('')}
       </div>`
    : '';

  div.innerHTML = `
    <div class="msg-avatar">${isUser ? '👤' : '✦'}</div>
    <div class="msg-body">
      <div class="msg-label">${isUser ? currentUser.username : 'Abner'}</div>
      <div class="msg-bubble">${bubbleContent}</div>
      ${chipsHtml}
      ${!isUser ? `
      <div class="feedback-row">
        <button onclick="copyMsg(this, ...)">📋</button>
        <button onclick="sendFeedback('${interactionId}',true,this)">👍</button>
        <button onclick="sendFeedback('${interactionId}',false,this)">👎</button>
      </div>` : ''}
    </div>`;

  msgs.appendChild(div);
  msgs.scrollTop = msgs.scrollHeight;  // rola para a mensagem mais recente
}
```

**`document.createElement('div')`** — cria um novo elemento HTML dinamicamente.  
**`msgs.appendChild(div)`** — adiciona ao final da lista de mensagens.  
**`msgs.scrollTop = msgs.scrollHeight`** — rola para o fim. `scrollHeight` é a altura
total do conteúdo (incluindo o que está fora da tela). Definir `scrollTop` como esse
valor rola até o fim.

### Indicador de Digitação (Typing)

```javascript
function appendTyping() {
  const id = 'typing-' + Date.now();  // ID único (timestamp)
  const div = document.createElement('div');
  div.className = 'msg bot';
  div.id = id;
  div.innerHTML = `
    <div class="msg-avatar">✦</div>
    <div class="msg-body">
      <div class="msg-label">Abner</div>
      <div class="msg-bubble">
        <div class="typing-indicator">
          <span></span>  <!-- bolinha 1 -->
          <span></span>  <!-- bolinha 2 — delay 0.2s -->
          <span></span>  <!-- bolinha 3 — delay 0.4s -->
        </div>
      </div>
      <div class="typing-hint">Abner está analisando os documentos…</div>
    </div>`;
  msgs.appendChild(div);
  return id;
}

function removeTyping(id) {
  document.getElementById(id)?.remove();  // ?. = optional chaining (não falha se null)
}
```

```css
/* Animação das bolinhas pulsando */
.typing-indicator span {
  width: 6px; height: 6px;
  background: var(--muted);
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: .2s; }
.typing-indicator span:nth-child(3) { animation-delay: .4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }    /* posição normal */
  30%           { transform: translateY(-4px); }  /* sobe 4px */
}
```

### Textarea que Cresce Automaticamente

```javascript
const textarea = document.getElementById('question');

textarea.addEventListener('input', () => {
  textarea.style.height = 'auto';           // reseta para calcular corretamente
  textarea.style.height = Math.min(textarea.scrollHeight, 100) + 'px';
  // scrollHeight = altura do conteúdo digitado
  // Math.min(..., 100) = limita a altura máxima em 100px
});

// Enter envia, Shift+Enter cria nova linha
textarea.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();  // evita a quebra de linha padrão do Enter
    sendMessage();
  }
});
```

**`textarea.scrollHeight`** — altura que o texto ocupa. Cresce conforme o usuário digita.
Sem o `height = 'auto'` antes, o cálculo ficaria errado ao apagar texto.

### Sessão de Chat com sessionStorage

```javascript
// Após receber resposta do backend
if (data.sessionId) {
  currentSessionId = data.sessionId;
  // Persiste na aba — ao recarregar a página, a sessão é restaurada
  sessionStorage.setItem('ragbot_session_' + currentUser.username, currentSessionId);
  updateSessionChip();  // mostra "sessão abc123..." na topbar
}

// No login, restaura a sessão anterior (se a aba ainda estava aberta)
currentSessionId = sessionStorage.getItem('ragbot_session_' + currentUser.username) || null;
```

**`sessionStorage`** — como o `localStorage`, mas dura apenas enquanto a aba está aberta.
Fechar a aba limpa o `sessionStorage`. Ideal para dados de sessão.

---

## 10. Markdown no Navegador — parseMarkdown()

A IA (Groq) frequentemente responde com formatação Markdown — **negrito**, `código`,
tabelas, listas, títulos. O frontend tem um parser próprio para converter isso em HTML.

```javascript
function parseMarkdown(text) {
  if (!text) return '';
  let html = escHtml(text);  // PRIMEIRO escapa o HTML (segurança)

  // Títulos: ## Texto → <h2>Texto</h2>
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  html = html.replace(/^## (.+)$/gm,  '<h2>$1</h2>');
  html = html.replace(/^# (.+)$/gm,   '<h1>$1</h1>');

  // Negrito e Itálico: **texto** → <strong>texto</strong>
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/\*(.+?)\*/g,     '<em>$1</em>');

  // Código inline: `código` → <code>código</code>
  html = html.replace(/`([^`\n]+)`/g, '<code class="md-code">$1</code>');

  // Blocos de código: ```\ncodigo\n``` → <pre><code>...</code></pre>
  html = html.replace(/```[\w]*\n?([\s\S]*?)```/g,
    '<pre class="md-pre"><code>$1</code></pre>');

  // Tabelas GFM (GitHub Flavored Markdown)
  html = html.replace(/(\|.+\|\n\|[-| :]+\|\n(?:\|.+\|\n?)+)/g, match => {
    const lines = match.trim().split('\n');
    const headers = lines[0].split('|').filter(c => c.trim());
    const rows    = lines.slice(2);  // pula linha de separadores (|---|---|)
    return `<div class="md-table-wrap"><table class="md-table">
      <thead><tr>${headers.map(h => `<th>${h.trim()}</th>`).join('')}</tr></thead>
      <tbody>${rows.map(r => {
        const cells = r.split('|').filter(c => c.trim());
        return `<tr>${cells.map(c => `<td>${c.trim()}</td>`).join('')}</tr>`;
      }).join('')}</tbody>
    </table></div>`;
  });

  // Listas não ordenadas: - item → <li>item</li>
  html = html.replace(/^[-*] (.+)$/gm, '<li>$1</li>');
  html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');

  // Listas ordenadas: 1. item → <li>item</li>
  html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');

  // Quebras de linha
  html = html.replace(/\n/g, '<br/>');

  return html;
}
```

**Por que não usar uma biblioteca de Markdown?**  
Uma biblioteca como `marked.js` pesaria ~50 KB. Para as respostas de um chatbot
corporativo, este parser simples cobre 95% dos casos com zero dependências.

**`/^## (.+)$/gm`** — expressão regular:
- `^` = início da linha
- `## ` = dois sustenidos e espaço (literal)
- `(.+)` = captura o resto da linha (grupo de captura)
- `$` = fim da linha
- `g` = global (substitui todas as ocorrências)
- `m` = multiline (^ e $ se aplicam a cada linha)

**`$1`** na substituição — referencia o primeiro grupo de captura `(.+)`.

---

## 11. Upload de Arquivo com Drag and Drop

### Duas formas de fazer upload

```html
<div class="upload-zone" id="uploadZone">
  <!-- Input invisível que cobre toda a zona -->
  <input type="file" id="fileInput" accept=".pdf,.csv" multiple/>
  <div>📂</div>
  <div><strong>Clique ou arraste</strong><br/>PDF e CSV</div>
</div>
```

```css
.upload-zone {
  position: relative;  /* necessário para o input absoluto dentro */
}

.upload-zone input[type=file] {
  position: absolute;
  inset: 0;            /* cobre toda a zona */
  opacity: 0;          /* invisível — o estilo é do div pai */
  cursor: pointer;
}
```

O truque: o `<input type="file">` é invisível (`opacity: 0`) mas ocupa toda a área
da zona de upload. Clicar em qualquer ponto da zona abre o diálogo de arquivo.

### Drag and Drop

```javascript
function initUpload() {
  const zone = document.getElementById('uploadZone');

  // Quando arrasta arquivo sobre a zona
  zone.addEventListener('dragover', e => {
    e.preventDefault();                  // OBRIGATÓRIO — sem isso o drop não funciona
    zone.classList.add('drag-over');     // destaca visualmente
  });

  // Quando sai sem soltar
  zone.addEventListener('dragleave', () => zone.classList.remove('drag-over'));

  // Quando solta o arquivo
  zone.addEventListener('drop', e => {
    e.preventDefault();
    zone.classList.remove('drag-over');
    [...e.dataTransfer.files].forEach(uploadFile);  // processa cada arquivo
  });

  // Via clique no input
  document.getElementById('fileInput').addEventListener('change', e => {
    [...e.target.files].forEach(uploadFile);
    e.target.value = '';  // limpa para permitir o mesmo arquivo novamente
  });
}
```

**`e.dataTransfer.files`** — lista de arquivos arrastados.  
**`[...lista]`** — spread operator, converte `FileList` (tipo especial) para Array normal.

### Enviando o Arquivo (Multipart)

```javascript
async function uploadFile(file) {
  // Validação de tipo no frontend
  const allowed = ['application/pdf', 'text/csv', 'application/csv'];
  if (!allowed.includes(file.type) && !file.name.match(/\.(pdf|csv)$/i)) {
    showToast('Formato não suportado: ' + file.name, 'error');
    return;
  }

  // Mostra barra de progresso animada
  document.getElementById('uploadProgress').style.display = 'block';
  document.getElementById('progressBar').style.width = '60%';

  // FormData — equivalente ao multipart/form-data do HTTP
  const form = new FormData();
  form.append('file', file);  // mesmo nome do @RequestParam("file") no Java

  const res = await fetch('/api/documents?namespaceId=' + currentNamespaceId, {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + token },
    // NÃO definir Content-Type aqui — o navegador define com o boundary correto
    body: form
  });

  if (res.status === 202) {  // 202 Accepted = processamento em background iniciado
    const result = await res.json();
    showToast('⏳ ' + file.name + ' — processando em background…', 'success');
    pollProcessingStatus(result.documentId, file.name);
  }
}
```

**`FormData`** — objeto que representa um formulário multipart. O `fetch` converte
automaticamente para o formato `multipart/form-data` que o Spring espera.

**Por que não definir `Content-Type`?**  
O `multipart/form-data` precisa de um `boundary` (separador) gerado pelo navegador.
Se você definir `Content-Type: multipart/form-data` manualmente, não inclui esse boundary
e a requisição falha. Deixar o navegador definir resolve automaticamente.

---

## 12. Polling — Verificando Status em Loop

Após o upload, o documento fica em status `PROCESSING` enquanto é indexado em background.
O frontend verifica periodicamente o status:

```javascript
const processingPollers = {};  // mapa: documentId → intervalId

function pollProcessingStatus(documentId, fileName) {
  if (processingPollers[documentId]) return;  // já está verificando este doc

  let attempts = 0;
  const MAX_ATTEMPTS = 120;  // 120 × 1.5s = 3 minutos máximo

  processingPollers[documentId] = setInterval(async () => {
    attempts++;

    // Timeout: para de verificar após 3 minutos
    if (attempts > MAX_ATTEMPTS) {
      clearInterval(processingPollers[documentId]);
      delete processingPollers[documentId];
      showToast('⚠️ Tempo limite atingido para: ' + fileName, 'error');
      return;
    }

    const res = await fetch('/api/documents/' + documentId + '/status', {
      headers: { 'Authorization': 'Bearer ' + token }
    });
    const data = await res.json();

    if (data.processingStatus === 'READY') {
      clearInterval(processingPollers[documentId]);
      delete processingPollers[documentId];
      showToast('✅ ' + fileName + ' pronto para consulta!', 'success');
      loadDocuments();  // atualiza a lista
    } else if (data.processingStatus === 'FAILED') {
      clearInterval(processingPollers[documentId]);
      delete processingPollers[documentId];
      showToast('❌ Falha ao processar ' + fileName, 'error');
    }
    // Se PROCESSING → continua verificando
  }, 1500);  // verifica a cada 1.5 segundos
}
```

**`setInterval(função, ms)`** — executa a função repetidamente a cada `ms` milissegundos.
Retorna um ID que pode ser passado para `clearInterval()` para parar.

**Por que guardar em `processingPollers`?**  
Se o usuário fizer múltiplos uploads ao mesmo tempo, cada documento precisa do próprio
timer. O objeto `processingPollers` garante que cada `documentId` tem apenas um timer
ativo — evita duplicação.

---

## 13. Dashboard de Qualidade RAG

O Dashboard é exclusivo para ADMIN e mostra métricas das interações:

```javascript
async function loadDashboard() {
  const res = await fetch('/api/admin/dashboard', {
    headers: { 'Authorization': 'Bearer ' + token }
  });
  const d = await res.json();

  // Métricas do backend:
  // d.totalInteractions — total de perguntas feitas
  // d.answered          — respondidas com contexto
  // d.notFound          — sem resultado nos documentos
  // d.answeredRate      — percentual de resposta (0-100)
  // d.positiveFeedback  — thumbs up
  // d.negativeFeedback  — thumbs down
  // d.recentInteractions — últimas interações com detalhes

  document.getElementById('metrics-grid').innerHTML = `
    <div class="metric-card">
      <div class="metric-label">Total de Perguntas</div>
      <div class="metric-value">${d.totalInteractions}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">Taxa de Resposta</div>
      <!-- Cor muda por threshold: ≥70% verde, ≥40% amarelo, <40% vermelho -->
      <div class="metric-value ${d.answeredRate >= 70 ? 'green' : d.answeredRate >= 40 ? 'yellow' : 'red'}">
        ${d.answeredRate}%
      </div>
    </div>
    <div class="metric-card">
      <div class="metric-label">Feedback Positivo</div>
      <div class="metric-value green">${d.positiveFeedback}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">Feedback Negativo</div>
      <div class="metric-value ${d.negativeFeedback > 0 ? 'red' : ''}">
        ${d.negativeFeedback}
      </div>
    </div>`;

  // Lista de interações recentes com badge ANSWERED / NOT_FOUND
  document.getElementById('recent-interactions').innerHTML = d.recentInteractions.map(i => `
    <div class="interaction-item">
      <div class="interaction-q">${escHtml(i.question)}</div>
      <div class="interaction-meta">
        <span>👤 ${i.username}</span>
        <span class="badge ${i.classification === 'ANSWERED' ? 'badge-answered' : 'badge-notfound'}">
          ${i.classification}
        </span>
        <span>📄 ${i.chunksRetrieved} chunks</span>
        <span>${fmtDateTime(i.createdAt)}</span>
        ${i.feedbackPositive === true ? '<span>👍</span>' : i.feedbackPositive === false ? '<span>👎</span>' : ''}
      </div>
    </div>`).join('');
}
```

**Lógica de cores por threshold:**
```javascript
// Template literal com expressão ternária encadeada
`metric-value ${rate >= 70 ? 'green' : rate >= 40 ? 'yellow' : 'red'}`
//              ↑ se ≥70%       ↑ se ≥40%              ↑ se <40%
```

O CSS define as classes de cor:
```css
.metric-value.green  { color: var(--green); }   /* #3ecf8e */
.metric-value.yellow { color: var(--yellow); }  /* #f6c90e */
.metric-value.red    { color: var(--red); }     /* #ff6b6b */
```

**O que observar no Dashboard:**
- **Taxa de Resposta baixa** (< 40%) → documentos insuficientes ou mal formatados
- **Muitos NOT_FOUND** → usuários perguntando coisas fora dos documentos
- **Feedback negativo alto** → qualidade das respostas pode ser melhorada (ajustar topK, minScore)

---

## 14. Painel de Usuários

```javascript
async function loadUsers() {
  const res = await fetch('/api/users', {
    headers: { 'Authorization': 'Bearer ' + token }
  });
  const users = await res.json();

  document.getElementById('users-tbody').innerHTML = users.map(u => `
    <tr>
      <td>${escHtml(u.username)}</td>
      <td>${escHtml(u.email)}</td>
      <td>
        <!-- Dropdown para trocar papel do usuário -->
        <select class="role-select" onchange="changeRole('${u.id}', this.value)">
          <option value="VIEWER" ${u.role === 'VIEWER' ? 'selected' : ''}>VIEWER</option>
          <option value="EDITOR" ${u.role === 'EDITOR' ? 'selected' : ''}>EDITOR</option>
          <option value="ADMIN"  ${u.role === 'ADMIN'  ? 'selected' : ''}>ADMIN</option>
        </select>
      </td>
      <td>
        <span class="status-badge ${u.active ? 'status-active' : 'status-inactive'}">
          ${u.active ? 'Ativo' : 'Inativo'}
        </span>
      </td>
      <td>${u.lastLogin ? fmtDateTime(u.lastLogin) : '—'}</td>
      <td>
        <button onclick="toggleUserActive('${u.id}', ${u.active})">
          ${u.active ? '🚫 Desativar' : '✅ Reativar'}
        </button>
      </td>
    </tr>`).join('');
}

async function changeRole(userId, newRole) {
  await fetch('/api/users/' + userId + '/role', {
    method: 'PUT',
    headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ role: newRole })
  });
  showToast('Perfil atualizado.', 'success');
  loadUsers();
}
```

**`${u.role === 'VIEWER' ? 'selected' : ''}`** — o atributo HTML `selected` em um `<option>`
faz ele aparecer pré-selecionado no dropdown. Esta expressão ternária adiciona `selected`
apenas na opção que corresponde ao papel atual do usuário.

---

## 15. Log de Auditoria

```html
<div class="filter-bar">
  <input type="text" id="audit-actor" placeholder="Filtrar por usuário…"/>
  <select id="audit-action">
    <option value="">Todas as ações</option>
    <option>LOGIN_SUCCESS</option>
    <option>LOGIN_FAILED</option>
    <option>DOCUMENT_UPLOAD</option>
    <!-- ... -->
  </select>
  <input type="number" id="audit-limit" value="100" min="10" max="500"/>
  <button onclick="loadAudit()">Buscar</button>
</div>
```

```javascript
async function loadAudit() {
  const actor  = document.getElementById('audit-actor').value.trim();
  const action = document.getElementById('audit-action').value;
  const limit  = document.getElementById('audit-limit').value;

  // Monta query string dinamicamente
  const params = new URLSearchParams();
  if (actor)  params.append('actor',  actor);
  if (action) params.append('action', action);
  params.append('limit', limit);

  const res = await fetch('/api/admin/audit-log?' + params.toString(), {
    headers: { 'Authorization': 'Bearer ' + token }
  });
  const entries = await res.json();

  document.getElementById('audit-tbody').innerHTML = entries.map(e => `
    <tr>
      <td>${fmtDateTime(e.createdAt)}</td>
      <td>${escHtml(e.actor)}</td>
      <td>
        <!-- Cor do badge muda por tipo de ação -->
        <span class="audit-action ${e.action}">${e.action}</span>
      </td>
      <td>${escHtml(e.targetType || '—')}</td>
      <td>${escHtml(e.detail || '—')}</td>
      <td style="font-family:monospace;font-size:10px">${e.ipAddress || '—'}</td>
    </tr>`).join('');
}
```

**`URLSearchParams`** — API moderna para construir query strings.
Melhor que concatenar strings manualmente porque faz o encoding correto
(ex: espaços viram `%20`, não `+` ou erros).

```javascript
const params = new URLSearchParams();
params.append('actor', 'joao');
params.append('limit', '50');
params.toString();  // → "actor=joao&limit=50"
```

**Cores de badge por ação:**
```css
.audit-action                            { color: var(--accent); }  /* azul padrão */
.audit-action.LOGIN_FAILED               { color: var(--red); }
.audit-action.USER_DEACTIVATE            { color: var(--red); }
.audit-action.DOCUMENT_DELETE            { color: var(--yellow); }
.audit-action.LOGIN_SUCCESS              { color: var(--green); }
.audit-action.USER_ACTIVATE              { color: var(--green); }
.audit-action.CONFIG_UPDATE              { color: var(--yellow); }
```

---

## 16. Config IA — Sliders em Tempo Real

```html
<!-- Slider de topK — atualiza o valor exibido em tempo real -->
<div class="config-field">
  <label>
    Top-K (chunks recuperados)
    <span id="lbl-topK">3</span>  <!-- valor atual, atualizado pelo oninput -->
  </label>
  <input type="range" id="cfg-topK"
    min="1" max="20" step="1" value="3"
    oninput="document.getElementById('lbl-topK').textContent = this.value"/>
  <div class="hint-text">Quantidade de chunks recuperados (1–20)</div>
</div>

<!-- Toggle switch para busca híbrida -->
<label class="toggle-switch">
  <input type="checkbox" id="cfg-hybrid" checked/>
  <span class="toggle-slider"></span>  <!-- o CSS cria o visual do toggle -->
</label>
```

```javascript
async function saveRagConfig() {
  const config = {
    topK:             parseInt(document.getElementById('cfg-topK').value),
    minScore:         parseFloat(document.getElementById('cfg-minScore').value),
    shortChunkLength: parseInt(document.getElementById('cfg-shortChunk').value),
    hybridEnabled:    document.getElementById('cfg-hybrid').checked,
    semanticWeight:   parseFloat(document.getElementById('cfg-semWeight').value),
    monthlyTokenLimit:parseInt(document.getElementById('cfg-tokenLimit').value),
    maxHistoryMessages: parseInt(document.getElementById('cfg-maxHistory').value),
  };

  const res = await fetch('/api/admin/config', {
    method: 'PUT',
    headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
    body: JSON.stringify(config)
  });

  if (res.ok) {
    showToast('✅ Configuração salva! Efeito imediato.', 'success');
  } else {
    showToast('Erro ao salvar.', 'error');
  }
}
```

**`oninput="..."`** — evento disparado a cada mudança no slider (enquanto arrasta).
Diferente de `onchange` (que só dispara ao soltar), `oninput` é em tempo real.

**`input[type=range]`** — o elemento HTML de slider. O CSS `accent-color: var(--accent)`
define a cor da bolinha e da barra preenchida.

**Toggle switch** — é um checkbox estilizado com CSS:
```css
.toggle-switch input { opacity: 0; }  /* checkbox invisível */
.toggle-slider {                       /* barra visual */
  background: var(--border);
  border-radius: 99px;
}
.toggle-switch input:checked + .toggle-slider {
  background: var(--accent);          /* azul quando ativado */
}
.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(18px);        /* bolinha se move para direita */
}
```

---

## 17. Animações CSS

### fadeIn — Mensagens Aparecendo Suavemente

```css
.msg { animation: fadeIn .2s ease; }

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(5px); }  /* começa invisível, 5px abaixo */
  to   { opacity: 1; transform: none; }              /* chega visível, posição normal */
}
```

### shimmer — Barra de Upload

```css
.upload-bar {
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer {
  0%, 100% { opacity: 1; }    /* opaco */
  50%       { opacity: .6; }  /* semi-transparente */
}
```

### pulse — Indicador Online e Documentos Processando

```css
.status-dot { animation: pulse 2s infinite; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: .4; }  /* pisca suavemente */
}
```

### bounce — Indicador de Digitação

```css
@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30%           { transform: translateY(-4px); }
}
```

### slideIn — Toasts

```css
@keyframes slideIn {
  from { transform: translateX(16px); opacity: 0; }  /* vem da direita */
  to   { transform: none; opacity: 1; }
}
```

---

## 18. Toast — Sistema de Notificações

```javascript
function showToast(msg, type = 'success') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = 'toast ' + type;  // 'toast success' ou 'toast error'
  toast.textContent = msg;
  container.appendChild(toast);

  // Remove automaticamente após 3 segundos
  setTimeout(() => toast.remove(), 3000);
}
```

```css
.toast-container {
  position: fixed;
  bottom: 70px; right: 20px;      /* canto inferior direito */
  display: flex;
  flex-direction: column; gap: 6px;
  z-index: 500;                    /* na frente de tudo */
}
.toast {
  padding: 9px 14px;
  border-radius: 8px;
  animation: slideIn .2s ease;
  max-width: 280px;
}
.toast.success {
  background: rgba(62,207,142,.1);
  border: 1px solid rgba(62,207,142,.3);
  color: var(--green);
}
.toast.error {
  background: rgba(255,107,107,.1);
  border: 1px solid rgba(255,107,107,.3);
  color: var(--red);
}
```

**`setTimeout(() => toast.remove(), 3000)`** — remove o elemento do DOM após 3 segundos.
A função passada como primeiro argumento é uma arrow function (ES6).

Múltiplos toasts se empilham automaticamente graças ao `flex-direction: column; gap: 6px`
no container.

---

## 19. Modais

```html
<!-- O modal usa position:fixed para cobrir a tela inteira -->
<div id="modal-create-user" class="modal-overlay"
     style="display:none"
     onclick="closeModalOnOverlay(event)">  <!-- clique fora fecha -->
  <div class="modal"
       onclick="event.stopPropagation()">   <!-- impede que clique interno feche -->
    <h3>👤 Novo Usuário</h3>
    <!-- formulário... -->
  </div>
</div>
```

```javascript
// Fecha se clicou no overlay (fundo escuro), não no modal em si
function closeModalOnOverlay(e) {
  if (e.target === document.getElementById('modal-create-user')) {
    closeCreateUserModal();
  }
}

// Fecha ao pressionar Escape
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    closeCreateUserModal();
    closeCreateNsModal();
  }
});
```

**`event.stopPropagation()`** — impede que o evento se propague para os elementos pai.
Sem isso, clicar dentro do modal acionaria o `onclick` do overlay e fecharia o modal.

**`e.target`** — o elemento que foi clicado diretamente. Se clicar no overlay,
`e.target === overlay`. Se clicar dentro do modal, `e.target` é algum elemento interno.

---

## 20. Segurança no Frontend

### XSS — Cross-Site Scripting

O maior risco em frontends é XSS: exibir texto do usuário como HTML sem escapar,
permitindo que um atacante injete scripts maliciosos.

```javascript
// Função de escape — SEMPRE usar para texto de usuário ou de APIs externas
function escHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g,  '&amp;')    // & → &amp;
    .replace(/</g,  '&lt;')     // < → &lt;   (impede abertura de tags)
    .replace(/>/g,  '&gt;')     // > → &gt;
    .replace(/"/g,  '&quot;')   // " → &quot; (protege atributos)
    .replace(/'/g,  '&#39;');   // ' → &#39;
}
```

**Uso:**
```javascript
// ERRADO — vulnerável a XSS:
div.innerHTML = `<b>${username}</b>`;  // se username = '<script>...' → executa!

// CORRETO — seguro:
div.innerHTML = `<b>${escHtml(username)}</b>`;  // '<script>' vira texto literal
```

**Por que o Markdown aplica `escHtml` ANTES de processar?**

```javascript
function parseMarkdown(text) {
  let html = escHtml(text);  // 1. PRIMEIRO escapa
  // ... depois substitui padrões Markdown por HTML ...
}
```

Se invertesse a ordem (processar Markdown primeiro, depois escapar), os `<h1>` etc.
gerados pelo Markdown seriam escapados para `&lt;h1&gt;` e apareceriam como texto.

Ao escapar primeiro, qualquer HTML malicioso no texto (`<script>`) é neutralizado,
e depois os padrões Markdown (que não são HTML) são convertidos em HTML legítimo.

### O que NÃO foi implementado e seria importante em produção

| Risco | Mitigação recomendada |
|-------|----------------------|
| JWT em memória perdido ao recarregar | Cookie `HttpOnly; Secure; SameSite=Strict` |
| Sem CSP (Content Security Policy) | Header `Content-Security-Policy` no Nginx |
| Validação apenas no frontend | Backend sempre revalida (já implementado) |

---

## Resumo Rápido — Tecnologias do Frontend

| Tecnologia | Onde aparece | O que resolve |
|------------|-------------|---------------|
| **HTML5** | Estrutura de toda a tela | Formulários, tabelas, botões |
| **CSS3 Custom Properties** | `:root { --accent: ... }` | Temas e consistência de cores |
| **CSS Flexbox** | Layout sidebar + main | Elementos lado a lado que se ajustam |
| **CSS Grid** | Dashboard de métricas | Colunas que se adaptam à tela |
| **CSS Media Queries** | `@media (max-width:768px)` | Versão mobile |
| **CSS Animations** | `@keyframes fadeIn` | Transições suaves |
| **Vanilla JavaScript ES2022** | Todo o `<script>` | Lógica, SPA, chamadas REST |
| **Fetch API** | `fetch('/api/...')` | Chamadas HTTP assíncronas |
| **async/await** | `await fetch(...)` | Código assíncrono legível |
| **localStorage** | Tema dark/light | Preferência persiste entre sessões |
| **sessionStorage** | Session ID do chat | Persiste na aba, some ao fechar |
| **FormData** | Upload de arquivos | Multipart/form-data |
| **URLSearchParams** | Filtros de auditoria | Query strings seguras |
| **setInterval** | Polling de status | Verifica progresso periodicamente |
| **DOM manipulation** | `createElement`, `appendChild` | Adicionar mensagens no chat |
| **Template literals** | `` `<div>${var}</div>` `` | HTML dinâmico em strings |

---

*Documentação gerada em 2026-05-12 — Crivo AI (RAGBot)*  
*Todo o frontend está em `src/main/resources/static/index.html` (~1.900 linhas)*
