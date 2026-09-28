"""Verify a DataGrid inside el-splitter-panel can be dragged OUT in all directions.

Covers the reported bug: 'can't drag the table out of the splitter panel'.
Directions tested: down-right (already worked) and up-left (used to abort the drag).
"""
import sys
from playwright.sync_api import sync_playwright

URL = 'http://localhost:5173'


def setup(page):
    """Drop a splitter on the canvas, then a DataGrid into its first panel."""
    canvas = page.locator('.canvas-page').first
    c_box = canvas.bounding_box()

    splitter_item = page.locator('.material-item:has-text("分割面板")').first
    s_box = splitter_item.bounding_box()
    page.mouse.move(s_box['x'] + s_box['width'] / 2, s_box['y'] + s_box['height'] / 2)
    page.mouse.down()
    page.mouse.move(c_box['x'] + 400, c_box['y'] + 300, steps=10)
    page.mouse.up()
    page.wait_for_timeout(600)

    grid_item = page.locator('.material-item:has-text("数据表格")').first
    grid_item.scroll_into_view_if_needed()
    page.wait_for_timeout(200)
    g_box = grid_item.bounding_box()
    panel = page.locator('.lc-el-splitter-panel').first
    p_box = panel.bounding_box()
    page.mouse.move(g_box['x'] + g_box['width'] / 2, g_box['y'] + g_box['height'] / 2)
    page.mouse.down()
    page.mouse.move(p_box['x'] + p_box['width'] / 2, p_box['y'] + p_box['height'] / 2, steps=10)
    page.mouse.up()
    page.wait_for_timeout(600)


def grid_box(page):
    """Bounding box of the DataGrid's own .canvas-node (innermost, not the splitter)."""
    return page.evaluate('''() => {
        const w = document.querySelector('.data-grid-wrapper');
        if (!w) return null;
        const cn = w.closest('.canvas-node');
        if (!cn) return null;
        const r = cn.getBoundingClientRect();
        return { x: r.x, y: r.y, width: r.width, height: r.height, id: cn.id,
                 parentClass: cn.parentElement?.className || '' };
    }''')


def drag_out(page, tx, ty):
    g = grid_box(page)
    assert g, 'DataGrid not found'
    page.mouse.move(g['x'] + g['width'] / 2, g['y'] + g['height'] / 2)
    page.mouse.down()
    page.wait_for_timeout(150)
    page.mouse.move(tx, ty, steps=15)
    page.wait_for_timeout(150)
    page.mouse.up()
    page.wait_for_timeout(500)
    return grid_box(page)


def main() -> None:
    failures = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1400, "height": 900})
        page.on("console", lambda m: print(f"[CONSOLE] {m.text}") if "drag-debug" in m.text else None)

        for label, fx, fy in [
            ("down-right", 0.85, 0.85),
            ("up-left", 0.10, 0.10),
            ("up-right", 0.85, 0.10),
            ("down-left", 0.10, 0.85),
        ]:
            page.goto(URL)
            page.wait_for_load_state('networkidle')
            page.wait_for_timeout(700)
            setup(page)

            before = grid_box(page)
            canvas = page.locator('.canvas-page').first
            c_box = canvas.bounding_box()
            tx = c_box['x'] + c_box['width'] * fx
            ty = c_box['y'] + c_box['height'] * fy
            after = drag_out(page, tx, ty)

            moved_to_root = 'canvas-page' in (after['parentClass'] if after else '')
            print(f"{label:11s} target=({tx:.0f},{ty:.0f})  "
                  f"before parent={before['parentClass'][:20]!r}  "
                  f"after parent={(after['parentClass'][:20] if after else 'GONE')!r}  "
                  f"-> {'OK root' if moved_to_root else 'FAIL still in panel'}")
            if not moved_to_root:
                failures.append(label)

        browser.close()

    print()
    if failures:
        print(f"FAILED directions: {failures}")
        sys.exit(1)
    print("PASS: DataGrid can be dragged out of the splitter panel in all 4 directions")


if __name__ == '__main__':
    main()
