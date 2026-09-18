#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { createInterface } from "node:readline/promises";
import { fileURLToPath } from "node:url";

const DEFAULT_SHEET_URL =
  "https://docs.google.com/spreadsheets/d/1JeeVWAlUaR3pDChVEiM7FPODiEHnLc0KwD3oqUaB1Ac/edit?gid=0#gid=0";
const DEFAULT_MESSAGE =
  "Ola, {nome}! Participamos do Workshop TLC sobre IA. Gostaria de manter contato por aqui e continuar trocando experiencias sobre o tema. Abraco, Renan.";
const DEFAULT_STATE_PATH = path.resolve(
  process.cwd(),
  ".linkedin-connection-assistant-state.json",
);

function usage() {
  return `
Assistente manual para convites no LinkedIn

Uso:
  node tools/linkedin-connection-assistant.mjs [opcoes]

Opcoes:
  --sheet <url>              URL da planilha Google Sheets
  --gid <numero>             Aba da planilha (padrao: gid da URL ou 0)
  --limit <n>                Maximo de perfis processados nesta execucao
  --state <arquivo>          Arquivo local de progresso
  --message <texto>          Mensagem; use {nome} para o primeiro nome
  --dry-run                  Apenas valida e lista os perfis
  --help                     Mostra esta ajuda

O script abre um perfil por vez no navegador padrao. Ele nao clica em
"Conectar" e nao envia convites: voce confirma cada acao manualmente.
`.trim();
}

export function parseArgs(argv) {
  const options = {
    sheetUrl: DEFAULT_SHEET_URL,
    gid: undefined,
    limit: Number.POSITIVE_INFINITY,
    statePath: DEFAULT_STATE_PATH,
    message: DEFAULT_MESSAGE,
    dryRun: false,
    help: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    const next = () => {
      index += 1;
      if (index >= argv.length) {
        throw new Error(`Faltou o valor de ${argument}.`);
      }
      return argv[index];
    };

    if (argument === "--sheet") options.sheetUrl = next();
    else if (argument === "--gid") options.gid = next();
    else if (argument === "--limit") options.limit = Number(next());
    else if (argument === "--state") options.statePath = path.resolve(next());
    else if (argument === "--message") options.message = next();
    else if (argument === "--dry-run") options.dryRun = true;
    else if (argument === "--help" || argument === "-h") options.help = true;
    else throw new Error(`Opcao desconhecida: ${argument}`);
  }

  if (
    options.limit !== Number.POSITIVE_INFINITY &&
    (!Number.isInteger(options.limit) || options.limit < 1)
  ) {
    throw new Error("O limite deve ser um numero inteiro maior que zero.");
  }

  return options;
}

export function buildCsvUrl(sheetUrl, explicitGid) {
  const url = new URL(sheetUrl);
  const match = url.pathname.match(/\/spreadsheets\/d\/([^/]+)/);
  if (!match) throw new Error("URL do Google Sheets invalida.");

  const hashParams = new URLSearchParams(url.hash.replace(/^#/, ""));
  const gid = explicitGid ?? url.searchParams.get("gid") ?? hashParams.get("gid") ?? "0";
  if (!/^\d+$/.test(String(gid))) throw new Error("O gid precisa ser numerico.");

  return `https://docs.google.com/spreadsheets/d/${match[1]}/export?format=csv&gid=${gid}`;
}

export function parseCsv(text) {
  const rows = [];
  let row = [];
  let value = "";
  let quoted = false;

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];

    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        value += '"';
        index += 1;
      } else if (character === '"') {
        quoted = false;
      } else {
        value += character;
      }
    } else if (character === '"' && value.length === 0) {
      quoted = true;
    } else if (character === ",") {
      row.push(value);
      value = "";
    } else if (character === "\n") {
      row.push(value.replace(/\r$/, ""));
      rows.push(row);
      row = [];
      value = "";
    } else {
      value += character;
    }
  }

  if (value.length > 0 || row.length > 0) {
    row.push(value.replace(/\r$/, ""));
    rows.push(row);
  }

  return rows;
}

export function normalizeLinkedInUrl(value) {
  const match = String(value ?? "").match(
    /https?:\/\/(?:[a-z]{2,3}\.)?linkedin\.com\/in\/[a-z0-9._~%+-]+/i,
  );
  if (!match) return null;

  const url = new URL(match[0]);
  const segments = url.pathname.split("/").filter(Boolean);
  if (segments.length < 2 || segments[0].toLowerCase() !== "in") return null;
  return `https://www.linkedin.com/in/${segments[1]}/`;
}

export function extractProfiles(rows) {
  if (rows.length === 0) return [];

  const headers = rows[0].map((value) => value.trim().toLocaleUpperCase("pt-BR"));
  const linkedInIndex = headers.findIndex((value) => value === "LINKEDIN");
  if (linkedInIndex < 0) {
    throw new Error('A planilha precisa ter uma coluna chamada "LINKEDIN".');
  }

  const nameIndex = headers.findIndex((value) =>
    /^(NOME|NAME|PARTICIPANTE)$/.test(value),
  );
  const selectedNameIndex = nameIndex >= 0 ? nameIndex : 0;
  const seen = new Set();
  const profiles = [];

  for (const row of rows.slice(1)) {
    const url = normalizeLinkedInUrl(row[linkedInIndex]);
    if (!url || seen.has(url)) continue;
    seen.add(url);
    profiles.push({
      name: String(row[selectedNameIndex] ?? "").trim() || "Nome nao informado",
      url,
    });
  }

  return profiles;
}

function profileId(url) {
  return createHash("sha256").update(url).digest("hex");
}

async function fetchProfiles(sheetUrl, gid) {
  const csvUrl = buildCsvUrl(sheetUrl, gid);
  const response = await fetch(csvUrl, {
    headers: { "user-agent": "linkedin-connection-assistant/1.0" },
    redirect: "follow",
  });
  if (!response.ok) {
    throw new Error(
      `Nao foi possivel baixar a planilha (HTTP ${response.status}). ` +
        "Confira se o link pode ser acessado por quem possui a URL.",
    );
  }
  return extractProfiles(parseCsv(await response.text()));
}

async function loadState(statePath) {
  try {
    const state = JSON.parse(await readFile(statePath, "utf8"));
    return {
      version: 1,
      profiles: state.profiles ?? {},
    };
  } catch (error) {
    if (error.code === "ENOENT") {
      return { version: 1, profiles: {} };
    }
    throw new Error(`Nao foi possivel ler o progresso: ${error.message}`);
  }
}

async function saveState(statePath, state) {
  const temporaryPath = `${statePath}.tmp`;
  await writeFile(temporaryPath, `${JSON.stringify(state, null, 2)}\n`, {
    mode: 0o600,
  });
  await rename(temporaryPath, statePath);
}

function openInDefaultBrowser(url) {
  const commands = {
    darwin: ["open", [url]],
    linux: ["xdg-open", [url]],
    win32: ["cmd", ["/c", "start", "", url]],
  };
  const command = commands[process.platform];
  if (!command) {
    throw new Error(`Sistema nao suportado para abrir o navegador: ${process.platform}`);
  }

  const child = spawn(command[0], command[1], {
    detached: true,
    stdio: "ignore",
  });
  child.unref();
}

async function copyToClipboard(text) {
  const commands = {
    darwin: ["pbcopy", []],
    linux: ["xclip", ["-selection", "clipboard"]],
    win32: ["clip", []],
  };
  const command = commands[process.platform];
  if (!command) return false;

  const child = spawn(command[0], command[1], {
    stdio: ["pipe", "ignore", "ignore"],
  });
  child.stdin.end(text);
  const [exitCode] = await once(child, "close");
  return exitCode === 0;
}

export function renderMessage(template, name) {
  const firstName = String(name ?? "").trim().split(/\s+/)[0] || "tudo bem";
  return template.replaceAll("{nome}", firstName);
}

async function askAction(reader) {
  while (true) {
    const answer = (await reader.question(
      "Acao: [Enter] enviado, [j] ja conectado, [p] pular, [s] sair: ",
    ))
      .trim()
      .toLocaleLowerCase("pt-BR");
    if (answer === "") return "e";
    if (["e", "j", "p", "s"].includes(answer)) return answer;
    console.log("Pressione Enter ou digite j, p ou s.");
  }
}

export async function run(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) {
    console.log(usage());
    return;
  }

  const profiles = await fetchProfiles(options.sheetUrl, options.gid);
  if (profiles.length === 0) {
    throw new Error("Nenhum link de perfil do LinkedIn foi encontrado.");
  }

  if (options.dryRun) {
    console.log(`${profiles.length} perfis validos e unicos encontrados.`);
    for (const profile of profiles.slice(0, Math.min(options.limit, 10))) {
      console.log(`- ${profile.name}: ${profile.url}`);
    }
    if (profiles.length > 10 && options.limit > 10) console.log("- ...");
    return;
  }

  const state = await loadState(options.statePath);
  const pendingProfiles = profiles.filter(
    (profile) => !state.profiles[profileId(profile.url)],
  );
  const queue = pendingProfiles.slice(0, options.limit);

  console.log(`${profiles.length} perfis validos e unicos encontrados.`);
  console.log(`${pendingProfiles.length} ainda nao foram processados.`);
  console.log("Voce controla quando o proximo perfil sera aberto.");
  console.log("O envio do convite e sempre manual no navegador.\n");

  if (queue.length === 0) {
    console.log("Nao ha perfis pendentes para esta execucao.");
    return;
  }

  const reader = createInterface({ input: process.stdin, output: process.stdout });
  try {
    for (let index = 0; index < queue.length; index += 1) {
      const profile = queue[index];
      const message = renderMessage(options.message, profile.name);
      const copied = await copyToClipboard(message);
      openInDefaultBrowser(profile.url);

      console.log(`[${index + 1}/${queue.length}] ${profile.name}`);
      console.log(profile.url);
      console.log(`Mensagem${copied ? " copiada" : " pronta"}: ${message}`);
      console.log(
        copied
          ? 'No LinkedIn, clique em "Conectar", adicione uma nota e cole a mensagem.'
          : 'No LinkedIn, clique em "Conectar" e adicione a mensagem acima.',
      );
      console.log("Depois do envio, volte aqui e pressione Enter.");

      const action = await askAction(reader);
      if (action === "s") {
        console.log("Execucao encerrada. Este perfil continuara pendente.");
        break;
      }

      const id = profileId(profile.url);
      const status = { e: "sent", j: "already_connected", p: "skipped" }[action];
      const now = new Date().toISOString();
      state.profiles[id] = { status, updatedAt: now };
      await saveState(options.statePath, state);
      console.log(`Registrado: ${status}.\n`);
    }
  } finally {
    reader.close();
  }
}

const isMain =
  process.argv[1] &&
  fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (isMain) {
  run().catch((error) => {
    console.error(`Erro: ${error.message}`);
    process.exitCode = 1;
  });
}
