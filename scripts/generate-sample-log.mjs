#!/usr/bin/env node
// Generates a realistic Logstash-style JSON log (one object per line) for demos and load tests.
//
//   node scripts/generate-sample-log.mjs <output-file> [lines=100000] [--start=2026-10-15T00:00:00Z]
//
// ~170 bytes per line, plus stack traces on errors: 2.5 million lines is roughly 500 MB.
// Keep the output outside synced folders (OneDrive/Dropbox) when generating large files.
import { createWriteStream } from 'node:fs'
import { once } from 'node:events'

const [, , output, linesArg, ...rest] = process.argv
if (!output) {
  console.error('usage: node scripts/generate-sample-log.mjs <output-file> [lines=100000] [--start=ISO]')
  process.exit(1)
}
const lines = Number(linesArg ?? 100_000)
const start = Date.parse(rest.find((arg) => arg.startsWith('--start='))?.slice(8) ?? '2026-10-15T00:00:00Z')

// Small deterministic PRNG so the same arguments always produce the same file.
let seed = 42
const random = () => {
  seed = (seed * 1664525 + 1013904223) % 4294967296
  return seed / 4294967296
}
const pick = (items) => items[Math.floor(random() * items.length)]
const hex = (length) => Array.from({ length }, () => Math.floor(random() * 16).toString(16)).join('')

const loggers = [
  'com.insurance.integration.services.base.BaseServiceIntegration',
  'com.insurance.policy.PolicyService',
  'com.insurance.policy.PolicyRepository',
  'com.insurance.claims.ClaimsProcessor',
  'com.insurance.payments.PaymentGateway',
  'com.insurance.auth.TokenValidator',
  'org.springframework.web.servlet.DispatcherServlet',
  'org.hibernate.SQL',
  'io.netty.channel.DefaultChannelPipeline',
]
const threads = ['reactor-http-epoll-1', 'reactor-http-epoll-2', 'reactor-http-epoll-3', 'http-nio-8080-exec-1', 'http-nio-8080-exec-7', 'scheduling-1']
const users = ['alice', 'bob', 'carol', 'dave', 'erin', 'aaaaa', 'svc-batch']
const infoMessages = [
  '[HTTP request] POST https://local/uri',
  '[HTTP response] 200 OK in 41 ms',
  'Policy {id} loaded',
  'Claim {id} submitted for review',
  'Token validated for subject {user}',
  'Cache hit for key policy:{id}',
]
const warnMessages = ['Slow query detected: {n} ms', 'Retrying request, attempt {n}', 'Deprecated endpoint /v1/policies called']
const errorMessages = ['Payment gateway timeout after {n} ms', 'Failed to persist claim {id}', 'Connection reset by peer']
const trace = `java.lang.IllegalStateException: boom
\tat com.insurance.payments.PaymentGateway.charge(PaymentGateway.java:118)
\tat com.insurance.claims.ClaimsProcessor.settle(ClaimsProcessor.java:76)
\tat reactor.core.publisher.MonoFlatMap$FlatMapMain.onNext(MonoFlatMap.java:178)
Caused by: java.net.SocketTimeoutException: Read timed out
\tat java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:288)`

const fill = (template) =>
  template.replace('{id}', String(100000 + Math.floor(random() * 900000))).replace('{n}', String(Math.floor(random() * 5000))).replace('{user}', pick(users))

const traces = Array.from({ length: Math.max(50, Math.floor(lines / 40)) }, () => hex(32))
const out = createWriteStream(output)
let time = start

for (let i = 0; i < lines; i++) {
  time += Math.floor(random() * 40)
  const roll = random()
  const level = roll < 0.004 ? 'TRACE' : roll < 0.15 ? 'DEBUG' : roll < 0.85 ? 'INFO' : roll < 0.94 ? 'WARN' : roll < 0.999 ? 'ERROR' : 'FATAL'

  let line
  if (random() < 0.00005) {
    line = 'unexpected plain text line from a misconfigured appender'
  } else {
    const entry = {
      '@timestamp': new Date(time).toISOString().replace('Z', `${String(Math.floor(random() * 1000)).padStart(3, '0')}000Z`),
      level,
      thread_name: pick(threads),
      logger_name: pick(loggers),
      message: fillMessage(level),
      trace_id: pick(traces),
      trace_flags: '00',
      span_id: hex(16),
      username: pick(users),
    }
    if (level === 'ERROR' || level === 'FATAL') entry.stack_trace = trace
    line = JSON.stringify(entry)
  }
  if (!out.write(`${line}\n`)) await once(out, 'drain')
}

function fillMessage(level) {
  if (level === 'WARN') return fill(pick(warnMessages))
  if (level === 'ERROR' || level === 'FATAL') return fill(pick(errorMessages))
  return fill(pick(infoMessages))
}

out.end()
await once(out, 'finish')
console.log(`wrote ${lines.toLocaleString()} lines to ${output}`)
