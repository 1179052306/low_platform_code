"""Test DataGrid inside el-splitter-panel: selection box, resize, drag-out."""
from playwright.sync_api import sync_playwright
import json

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 900})
    page.on("console", lambda msg: print(f"[CONSOLE] {msg.type}: {msg.text}"))

    page.goto('http://localhost:5173')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1000)

    # Drag splitter to canvas
    splitter = page.locator('.material-item:has-text("分割面板")').first
    canvas = page.locator('.canvas-page').first
    sBox = splitter.bounding_box()
    cBox = canvas.bounding_box()
    print(f"Splitter box: {sBox}")
    print(f"Canvas box: {cBox}")

    page.mouse.move(sBox['x'] + sBox['width']/2, sBox['y'] + sBox['height']/2)
    page.mouse.down()
    page.wait_for_timeout(300)
    page.mouse.move(cBox['x'] + 300, cBox['y'] + 200, steps=15)
    page.wait_for_timeout(300)
    page.mouse.up()
    page.wait_for_timeout(1000)
    page.screenshot(path='test_screenshots/dg_01_splitter.png')
    print("Screenshot dg_01 saved")

    # Scroll material panel to bring DataGrid into view
    grid = page.locator('.material-item:has-text("数据表格")').first
    grid.scroll_into_view_if_needed()
    page.wait_for_timeout(300)

    # Drag DataGrid into first splitter panel
    panels = page.locator('.lc-el-splitter-panel').all()
    print(f"Panels: {len(panels)}")
    pBox = panels[0].bounding_box()
    gBox = grid.bounding_box()
    print(f"Panel box: {pBox}")
    print(f"Grid item box: {gBox}")

    page.mouse.move(gBox['x'] + gBox['width']/2, gBox['y'] + gBox['height']/2)
    page.mouse.down()
    page.wait_for_timeout(300)
    page.mouse.move(pBox['x'] + pBox['width']/4, pBox['y'] + pBox['height']/4, steps=15)
    page.wait_for_timeout(300)
    page.mouse.up()
    page.wait_for_timeout(1000)
    page.screenshot(path='test_screenshots/dg_02_grid_in_panel.png')
    print("Screenshot dg_02 saved")

    # Click the DataGrid to select it
    grid_nodes = page.locator('.canvas-node-positioned.canvas-node-selected-plain, .canvas-node-positioned').all()
    print(f"Positioned nodes: {len(grid_nodes)}")
    for n in grid_nodes:
        print(f"  {n.get_attribute('id')}: {n.get_attribute('class')}")

    # Find the DataGrid canvas-node (inside splitter panel)
    grid_node = None
    for node in page.locator('.canvas-node').all():
        cls = node.get_attribute('class') or ''
        # Check if parent chain includes splitter panel
        is_in_panel = node.evaluate('''el => {
            let cur = el.parentElement;
            while (cur) {
                if (cur.classList && cur.classList.contains('lc-el-splitter-panel')) return true;
                cur = cur.parentElement;
            }
            return false;
        }''')
        if is_in_panel:
            grid_node = node
            print(f"Found grid node candidate: {node.get_attribute('id')} class={cls}")
            break

    if grid_node is None:
        # List all canvas nodes for debugging
        print("All canvas nodes:")
        for node in page.locator('.canvas-node').all():
            print(f"  {node.get_attribute('id')}: {node.get_attribute('class')}")
        print("DataGrid node not found in panel")
        browser.close()
        exit(1)

    box = grid_node.bounding_box()
    print(f"DataGrid node box: {box}")
    page.mouse.click(box['x'] + 10, box['y'] + 10)
    page.wait_for_timeout(500)
    page.screenshot(path='test_screenshots/dg_03_grid_selected.png')
    print("Screenshot dg_03 saved")

    # Resize east handle: drag right by 50px
    sel = page.locator('.canvas-node-selected').first
    box = sel.bounding_box()
    print(f"Selected box before resize: {box}")

    # East handle is at right center
    ex = box['x'] + box['width']
    ey = box['y'] + box['height'] / 2
    print(f"East handle: ({ex}, {ey})")
    page.mouse.move(ex, ey)
    page.mouse.down()
    page.wait_for_timeout(200)
    page.mouse.move(ex + 50, ey, steps=10)
    page.wait_for_timeout(200)
    page.mouse.up()
    page.wait_for_timeout(500)
    page.screenshot(path='test_screenshots/dg_04_after_e_resize.png')
    box_after = sel.bounding_box()
    print(f"Selected box after east resize: {box_after}")

    # Resize south handle: drag down by 50px
    box = sel.bounding_box()
    sx = box['x'] + box['width'] / 2
    sy = box['y'] + box['height']
    print(f"South handle: ({sx}, {sy})")
    page.mouse.move(sx, sy)
    page.mouse.down()
    page.wait_for_timeout(200)
    page.mouse.move(sx, sy + 50, steps=10)
    page.wait_for_timeout(200)
    page.mouse.up()
    page.wait_for_timeout(500)
    page.screenshot(path='test_screenshots/dg_05_after_s_resize.png')
    box_after = sel.bounding_box()
    print(f"Selected box after south resize: {box_after}")

    # Try to drag the DataGrid out of the panel to canvas root
    box = sel.bounding_box()
    start_x = box['x'] + box['width'] / 2
    start_y = box['y'] + box['height'] / 2
    target_x = cBox['x'] + cBox['width'] - 100
    target_y = cBox['y'] + cBox['height'] - 100
    print(f"Drag from ({start_x}, {start_y}) to ({target_x}, {target_y})")
    page.mouse.move(start_x, start_y)
    page.mouse.down()
    page.wait_for_timeout(300)
    page.mouse.move(target_x, target_y, steps=20)
    page.wait_for_timeout(300)
    page.mouse.up()
    page.wait_for_timeout(1000)
    page.screenshot(path='test_screenshots/dg_06_after_drag_out.png')
    print("Screenshot dg_06 saved")

    # Check if DataGrid is now a root-level node
    result = page.evaluate('''() => {
        const nodes = document.querySelectorAll('.canvas-node-positioned');
        return Array.from(nodes).map(el => ({
            id: el.id,
            class: el.className,
            rect: el.getBoundingClientRect().toJSON(),
            parentClass: el.parentElement?.className?.substring(0, 80) || '',
        }));
    }''')
    print(f"Positioned nodes after drag-out: {json.dumps(result, indent=2, ensure_ascii=False)}")

    browser.close()
    print("\nDone!")
