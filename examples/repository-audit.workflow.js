export const meta = {
  name: 'repository_audit',
  description: 'Audit a repository from several independent angles and synthesize the result',
  phases: [
    { title: 'Inventory' },
    { title: 'Parallel audit' },
    { title: 'Synthesis' },
  ],
}

phase('Inventory')
const inventory = await agent(
  'Inspect the repository structure. Return the main modules, entrypoints, and risky areas to audit.',
  { label: 'repo inventory' },
)

phase('Parallel audit')
const checks = [
  'security-sensitive trust boundaries',
  'duplicated plugin or extension contracts',
  'missing tests around public extension behavior',
  'documentation drift between README and implementation',
]

const findings = await parallel(
  checks.map((check) => () => agent(
    'Using this inventory:\n' + inventory + '\n\nAudit for: ' + check + '. Return concise findings with file paths and evidence.',
    { label: check },
  )),
)

phase('Synthesis')
return await agent(
  'Deduplicate, rank, and verify these audit findings. Return verdict, top risks, evidence, and next actions:\n\n' + findings.join('\n\n---\n\n'),
  { label: 'final synthesis' },
)
