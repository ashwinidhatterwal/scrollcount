// Optional static policy export. Deletion stays on the secure Worker endpoint.
import {mkdirSync,writeFileSync} from 'node:fs';
import {publicPage,policyReady} from '../backend/public-pages.mjs';
import {policy} from '../backend/release-info.mjs';
if(!policyReady()&&!process.argv.includes('--preview')){
 console.error('Configure the public email before exporting a publishable policy.');process.exit(1);
}
mkdirSync(new URL('../website/',import.meta.url),{recursive:true});
const html=(await publicPage('/privacy',policy.server).text())
 .replaceAll('href="/privacy"',`href="${policy.server}/privacy"`)
 .replaceAll('href="/delete"',`href="${policy.server}/delete"`);
writeFileSync(new URL('../website/privacy.html',import.meta.url),html);
mkdirSync(new URL('../docs/',import.meta.url),{recursive:true});
writeFileSync(new URL('../docs/privacy.html',import.meta.url),html);
console.log(policyReady()?'Static privacy page exported.':'Preview exported with publication-incomplete notice. Configure email and regenerate before hosting.');
