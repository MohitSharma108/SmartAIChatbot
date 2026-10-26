const messagesElement = document.querySelector('#messages');
const welcomeElement = document.querySelector('#welcome');
const composer = document.querySelector('#composer');
const input = document.querySelector('#messageInput');
const sendButton = document.querySelector('#sendButton');
const newChatButton = document.querySelector('#newChatButton');

let history = [];
let busy = false;

function addMessage(role, content, kind = '') {
  welcomeElement?.remove();
  const wrapper = document.createElement('div');
  wrapper.className = `message ${role} ${kind}`.trim();
  const bubble = document.createElement('div');
  bubble.className = 'bubble';
  bubble.textContent = content;
  wrapper.appendChild(bubble);
  messagesElement.appendChild(wrapper);
  messagesElement.scrollTop = messagesElement.scrollHeight;
}

function setBusy(value) {
  busy = value;
  sendButton.disabled = value;
  input.disabled = value;
  sendButton.textContent = value ? '…' : '➤';
}

async function sendMessage(rawText) {
  const message = rawText.trim();
  if (!message || busy) return;

  addMessage('user', message);
  input.value = '';
  input.style.height = 'auto';
  setBusy(true);

  try {
    const response = await fetch('/chat', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ message, history }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error?.message || 'The server could not answer.');
    const reply = String(body.reply || '').trim();
    if (!reply) throw new Error('The AI returned an empty answer.');
    addMessage('assistant', reply);
    history = [...history, { role: 'user', content: message }, { role: 'assistant', content: reply }].slice(-20);
  } catch (error) {
    addMessage('error', error.message || 'Something went wrong.', 'error');
  } finally {
    setBusy(false);
    input.focus();
  }
}

composer.addEventListener('submit', (event) => {
  event.preventDefault();
  sendMessage(input.value);
});

input.addEventListener('input', () => {
  input.style.height = 'auto';
  input.style.height = `${Math.min(input.scrollHeight, 140)}px`;
});

document.querySelectorAll('[data-prompt]').forEach((button) => {
  button.addEventListener('click', () => sendMessage(button.dataset.prompt));
});

newChatButton.addEventListener('click', () => {
  history = [];
  messagesElement.innerHTML = '';
  const welcome = document.createElement('div');
  welcome.className = 'welcome';
  welcome.id = 'welcome';
  welcome.innerHTML = '<div class="orb">✦</div><h2>How can I help?</h2><p>Ask a question or choose a starter prompt.</p>';
  messagesElement.appendChild(welcome);
  input.focus();
});
