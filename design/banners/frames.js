const { chromium } = require('playwright');
(async () => {
  const [json, outPrefix, count, l1, l2] = process.argv.slice(2);
  const data = JSON.parse(require('fs').readFileSync(json, 'utf8'));
  const b = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const p = await b.newPage({ viewport: { width: 1400, height: 460 }, deviceScaleFactor: 1 });
  p.on('console', m => console.log('console:', m.text()));
  p.on('pageerror', e => console.log('error:', e.message));
  await p.goto('file://' + process.cwd() + '/preview.html');
  await p.evaluate(([d, a, b]) => window.load(d, a, b), [data, l1 || '', l2 || '']);
  await p.waitForTimeout(500);
  const n = +count;
  for (let i = 0; i < n; i++) {
    await p.evaluate(f => window.anim.goToAndStop(f, true), i);
    await p.screenshot({ path: `${outPrefix}${String(i).padStart(3, '0')}.png` });
  }
  await b.close();
})();
