const { test } = require("node:test"),
  assert = require("node:assert/strict"),
  vm = require("node:vm"),
  fs = require("node:fs");
function open(hash) {
  const elements = {},
    history = [],
    ctx = {
      URLSearchParams,
      location: { hash, pathname: "/call/" },
      history: { replaceState: (...a) => history.push(a) },
      window: { addEventListener() {} },
      document: { getElementById: (id) => (elements[id] ??= {}) },
      setTimeout,
      clearTimeout,
    };
  vm.runInNewContext(fs.readFileSync("caller/voice.js", "utf8"), ctx);
  return { elements, history, ctx };
}
test("missing invite fails closed and clears fragment", () => {
  const { elements, history } = open("");
  assert.equal(elements.start.disabled, true);
  assert.equal(history[0][2], "/call/");
});
test("permanent provider key is not accepted", () =>
  assert.equal(
    open("#token=key123&model=live&to=919999999999").elements.start.disabled,
    true,
  ));
test("share only after caller supplies text, to validated owner", () => {
  const { ctx, elements } = open(
    "#token=auth_tokens%2Ftest&model=live&to=919999999999",
  );
  assert.notEqual(elements.start.disabled, true);
  ctx.document.getElementById("note").value = " ";
  elements.share.onclick();
  assert.equal(ctx.location.href, undefined);
  elements.note.value = "Call tomorrow at 10.";
  elements.share.onclick();
  assert.equal(
    decodeURIComponent(ctx.location.href),
    "https://wa.me/919999999999?text=Mimi call brief:\nCall tomorrow at 10.",
  );
});
