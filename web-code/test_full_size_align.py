"""Verify that setting a control's width/height to 100% snaps it to its parent
container's left/top edge, so it can never overflow the canvas.

Case A: node at canvas root (parent = .canvas-page)
Case B: node inside el-splitter-panel (parent = .container-content / panel)
"""
import sys
from playwright.sync_api import sync_playwright

URL = 'http://localhost:5173'
TOL = 2  # px tolerance


def grid_info(page):
    """Rect of the DataGrid's own .canvas-node plus its offset inside the parent."""
    return page.evaluate('''() => {
        const w = document.querySelector('.data-grid-wrapper');
        if (!w) return null;
        const cn = w.closest('.canvas-node');
        if (!cn) return null;
        const parent = cn.parentElement;
        const r = cn.getBoundingClientRect();
        const pr = parent.getBoundingClientRect();
        const round = (o) => ({ left: Math.round(o.left), top: Math.round(o.top),
                                right: Math.round(o.right), bottom: Math.round(o.bottom),
                                width: Math.round(o.width), height: Math.round(o.height) });
        return {
            node: round(r), parent: round(pr),
            parentClass: parent.className || '',
            dx: Math.round(r.left - pr.left),
            dy: Math.round(r.top - pr.top),
        };
    }''')


def drop_material(page, name, x, y):
    item = page.locator(f'.material-item:has-text("{name}")').first
    item.scroll_into_view_if_needed()
    page.wait_for_timeout(150)
    b = item.bounding_box()
    page.mouse.move(b['x'] + b['width'] / 2, b['y'] + b['height'] / 2)
    page.mouse.down()
    page.mouse.move(x, y, steps=10)
    page.mouse.up()
    page.wait_for_timeout(600)


def set_css_size(page, label, value, unit='%'):
    """Set the 宽度/高度 row of the property panel."""
    inp = page.locator(f'.prop-item:has(.prop-label:has-text("{label}")) .css-unit-input').first
    sel = page.locator(f'.prop-item:has(.prop-label:has-text("{label}")) .css-unit-select').first
    sel.select_option(unit)
    page.wait_for_timeout(150)
    inp.fill(str(value))
    inp.press('Tab')
    page.wait_for_timeout(700)


def select_grid(page):
    info = grid_info(page)
    assert info, 'DataGrid not found'
    n = info['node']
    page.mouse.click(n['left'] + n['width'] / 2, n['top'] + n['height'] / 2)
    page.wait_for_timeout(400)
    return info


def check(label, cond, detail):
    status = 'PASS' if cond else 'FAIL'
    print(f'  [{status}] {label}: {detail}')
    return cond


def main() -> None:
    failures = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1400, "height": 900})

        # ---------------- Case A: root canvas ----------------
        print('Case A: DataGrid at canvas root')
        page.goto(URL)
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(700)
        canvas = page.locator('.canvas-page').first
        c = canvas.bounding_box()
        drop_material(page, '数据表格', c['x'] + 320, c['y'] + 260)

        before = select_grid(page)
        print(f"    before: dx={before['dx']} dy={before['dy']} parent={before['parentClass'][:24]!r}")
        if not check('node starts offset from container origin',
                     abs(before['dx']) > 5 or abs(before['dy']) > 5,
                     f"dx={before['dx']} dy={before['dy']}"):
            failures.append('A: initial offset')

        set_css_size(page, '宽度', 100)
        after_w = grid_info(page)
        print(f"    after width=100%: dx={after_w['dx']} dy={after_w['dy']}")
        if not check('width=100% snaps x to container left', abs(after_w['dx']) <= TOL,
                     f"dx={after_w['dx']}"):
            failures.append('A: width snap')
        if not check('width=100% does not overflow right',
                     after_w['node']['right'] <= after_w['parent']['right'] + TOL,
                     f"node.right={after_w['node']['right']} parent.right={after_w['parent']['right']}"):
            failures.append('A: width overflow')

        set_css_size(page, '高度', 100)
        after_h = grid_info(page)
        print(f"    after height=100%: dx={after_h['dx']} dy={after_h['dy']}")
        if not check('height=100% snaps y to container top', abs(after_h['dy']) <= TOL,
                     f"dy={after_h['dy']}"):
            failures.append('A: height snap')
        if not check('height=100% does not overflow bottom',
                     after_h['node']['bottom'] <= after_h['parent']['bottom'] + TOL,
                     f"node.bottom={after_h['node']['bottom']} parent.bottom={after_h['parent']['bottom']}"):
            failures.append('A: height overflow')

        # ---------------- Case B: inside splitter panel ----------------
        print('Case B: DataGrid inside el-splitter-panel')
        page.goto(URL)
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(700)
        canvas = page.locator('.canvas-page').first
        c = canvas.bounding_box()
        drop_material(page, '分割面板', c['x'] + 400, c['y'] + 300)
        panel = page.locator('.lc-el-splitter-panel').first
        pb = panel.bounding_box()
        # drop into the lower-right area of the panel so x/y are clearly non-zero
        drop_material(page, '数据表格', pb['x'] + pb['width'] * 0.72, pb['y'] + pb['height'] * 0.72)

        before = select_grid(page)
        print(f"    before: dx={before['dx']} dy={before['dy']} parent={before['parentClass'][:24]!r}")
        if not check('node starts offset inside panel',
                     abs(before['dx']) > 3 or abs(before['dy']) > 3,
                     f"dx={before['dx']} dy={before['dy']}"):
            failures.append('B: initial offset')

        set_css_size(page, '宽度', 100)
        after_w = grid_info(page)
        print(f"    after width=100%: dx={after_w['dx']} dy={after_w['dy']}")
        if not check('width=100% snaps x to panel left', abs(after_w['dx']) <= TOL,
                     f"dx={after_w['dx']}"):
            failures.append('B: width snap')

        set_css_size(page, '高度', 100)
        after_h = grid_info(page)
        print(f"    after height=100%: dx={after_h['dx']} dy={after_h['dy']}")
        if not check('height=100% snaps y to panel top', abs(after_h['dy']) <= TOL,
                     f"dy={after_h['dy']}"):
            failures.append('B: height snap')
        if not check('node stays inside panel after 100%/100%',
                     after_h['node']['right'] <= after_h['parent']['right'] + TOL
                     and after_h['node']['bottom'] <= after_h['parent']['bottom'] + TOL,
                     f"node={after_h['node']['right']}/{after_h['node']['bottom']} "
                     f"parent={after_h['parent']['right']}/{after_h['parent']['bottom']}"):
            failures.append('B: overflow')

        browser.close()

    print()
    if failures:
        print(f'FAILED checks: {failures}')
        sys.exit(1)
    print('PASS: 100% width/height auto-aligns the control to its container origin.')


if __name__ == '__main__':
    main()
