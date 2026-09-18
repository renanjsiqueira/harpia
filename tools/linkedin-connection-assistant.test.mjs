import assert from "node:assert/strict";
import test from "node:test";

import {
  buildCsvUrl,
  extractProfiles,
  normalizeLinkedInUrl,
  parseArgs,
  parseCsv,
  renderMessage,
} from "./linkedin-connection-assistant.mjs";

test("buildCsvUrl extrai a planilha e o gid do fragmento", () => {
  assert.equal(
    buildCsvUrl(
      "https://docs.google.com/spreadsheets/d/abc123/edit?usp=sharing#gid=42",
    ),
    "https://docs.google.com/spreadsheets/d/abc123/export?format=csv&gid=42",
  );
});

test("parseCsv trata virgulas, aspas e quebras de linha", () => {
  assert.deepEqual(parseCsv('NOME,LINKEDIN\r\n"Ana, Maria","https://x"\r\n'), [
    ["NOME", "LINKEDIN"],
    ["Ana, Maria", "https://x"],
  ]);
});

test("normalizeLinkedInUrl remove rastreamento e normaliza o dominio", () => {
  assert.equal(
    normalizeLinkedInUrl(
      "https://br.linkedin.com/in/exemplo-123?utm_source=share",
    ),
    "https://www.linkedin.com/in/exemplo-123/",
  );
  assert.equal(normalizeLinkedInUrl("https://linkedin.com/company/exemplo"), null);
});

test("extractProfiles usa a coluna LINKEDIN, remove invalidos e duplicados", () => {
  const profiles = extractProfiles([
    ["NOME", "LINKEDIN"],
    ["Ana", "https://linkedin.com/in/ana"],
    ["Ana duplicada", "https://www.linkedin.com/in/ana/?trk=abc"],
    ["Empresa", "https://linkedin.com/company/acme"],
  ]);

  assert.deepEqual(profiles, [
    { name: "Ana", url: "https://www.linkedin.com/in/ana/" },
  ]);
});

test("parseArgs aceita um limite para a execucao", () => {
  assert.equal(parseArgs(["--limit", "50"]).limit, 50);
});

test("renderMessage personaliza somente com o primeiro nome", () => {
  assert.equal(renderMessage("Ola, {nome}!", "Ana Maria Silva"), "Ola, Ana!");
});
