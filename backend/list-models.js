// Prints the model IDs your OpenAI project can use. Run: npm run models
import 'dotenv/config';
import OpenAI from 'openai';

if (!process.env.OPENAI_API_KEY || process.env.OPENAI_API_KEY.startsWith('your_')) {
  console.error('Set OPENAI_API_KEY in backend/.env first.');
  process.exit(1);
}

const clientOptions = { apiKey: process.env.OPENAI_API_KEY };
if (process.env.OPENAI_BASE_URL) {
  clientOptions.baseURL = process.env.OPENAI_BASE_URL;
}
const client = new OpenAI(clientOptions);

try {
  const ids = [];
  for await (const model of client.models.list()) {
    ids.push(model.id);
  }
  ids.sort().forEach((id) => console.log(id));
} catch (err) {
  console.error('Could not list models. HTTP status:', err?.status ?? 'n/a');
  process.exit(1);
}
