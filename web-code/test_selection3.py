"""Inject a test script into the running dev server to check selection visibility"""
from playwright.sync_api import sync_playwright
import json

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 900})

    page.goto('http://localhost:5173')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1000)

    # Get all material items
    items = page.evaluate('''() => {
        const items = document.querySelectorAll('.material-item');
        return Array.from(items).map((el, i) => ({
            index: i,
            text: el.textContent.trim(),
            draggable: el.draggable
        }));
    }''')
    print("Material items:")
    print(json.dumps(items, indent=2, ensure_ascii=False))

    # Use JavaScript drag-and-drop simulation
    # First, add a splitter to canvas programmatically
    result = page.evaluate('''() => {
        // Try to find the designer store
        const app = document.querySelector('#app').__vue_app__;
        if (!app) return {error: 'no vue app'};

        // Try to access the designer store through the global scope
        // Let's check if there's a way to add nodes programmatically
        const materialItems = document.querySelectorAll('.material-item');
        const splitterItem = Array.from(materialItems).find(el => el.textContent.includes('分割'));

        if (!splitterItem) return {error: 'no splitter item found'};

        // Get canvas
        const canvas = document.querySelector('.canvas-page') || document.querySelector('[class*="canvas"]');
        if (!canvas) return {error: 'no canvas found'};

        const sBox = splitterItem.getBoundingClientRect();
        const cBox = canvas.getBoundingClientRect();

        return {
            splitterText: splitterItem.textContent.trim(),
            splitterBox: sBox,
            canvasBox: cBox,
        };
    }''')
    print("\nPre-check result:")
    print(json.dumps(result, indent=2, ensure_ascii=False))

    # Drag splitter to canvas
    if isinstance(result, dict) and 'splitterBox' in result:
        sBox = result['splitterBox']
        cBox = result['canvasBox']

        # Drag
        page.mouse.move(sBox['x'] + sBox['width']/2, sBox['y'] + sBox['height']/2)
        page.mouse.down()
        page.wait_for_timeout(300)
        page.mouse.move(cBox['x'] + 300, cBox['y'] + 200, steps=20)
        page.wait_for_timeout(300)
        page.mouse.up()
        page.wait_for_timeout(1000)

        page.screenshot(path='test_screenshots/02_after_splitter.png')
        print("Screenshot 02 saved")

        # Check what's on canvas now
        canvas_nodes = page.evaluate('''() => {
            const nodes = document.querySelectorAll('.canvas-node');
            return Array.from(nodes).map(el => ({
                id: el.id,
                class: el.className,
                type: el.getAttribute('data-section') || '',
                rect: el.getBoundingClientRect().toJSON(),
            }));
        }''')
        print(f"\nCanvas nodes after splitter drop: {len(canvas_nodes)}")
        for n in canvas_nodes:
            print(f"  {n['id']}: {n['class'][:60]}... rect={n['rect']}")

        # Find empty container
        empty = page.evaluate('''() => {
            const els = document.querySelectorAll('*');
            const result = [];
            for (const el of els) {
                if (el.textContent.includes('拖入组件到此容器') && el.classList.contains('container-empty')) {
                    result.push({
                        tag: el.tagName,
                        class: el.className,
                        parent: el.parentElement?.tagName + ' ' + (el.parentElement?.className || ''),
                        rect: el.getBoundingClientRect().toJSON(),
                    });
                }
            }
            return result;
        }''')
        print(f"\nEmpty containers: {len(empty)}")
        for e in empty:
            print(f"  {e}")

        # Find text box material item
        tb_result = page.evaluate('''() => {
            const items = document.querySelectorAll('.material-item');
            const tb = Array.from(items).find(el => el.textContent.includes('文本') || el.textContent.includes('输入') || el.textContent.includes('单行'));
            if (!tb) return null;
            return {text: tb.textContent.trim(), rect: tb.getBoundingClientRect().toJSON()};
        }''')
        print(f"\nText box material: {tb_result}")

        if tb_result and len(empty) > 0:
            tb_rect = tb_result['rect']
            target_rect = empty[0]['rect']

            # Drag text box into splitter panel
            page.mouse.move(tb_rect['x'] + tb_rect['width']/2, tb_rect['y'] + tb_rect['height']/2)
            page.mouse.down()
            page.wait_for_timeout(300)
            page.mouse.move(target_rect['x'] + target_rect['width']/4, target_rect['y'] + target_rect['height']/4, steps=20)
            page.wait_for_timeout(300)
            page.mouse.up()
            page.wait_for_timeout(1000)

            page.screenshot(path='test_screenshots/03_after_textbox.png')
            print("Screenshot 03 saved")

            # Find the text box in canvas
            textbox_info = page.evaluate('''() => {
                // Find canvas-node that contains a dx textbox
                const nodes = document.querySelectorAll('.canvas-node');
                const result = [];
                for (const node of nodes) {
                    const input = node.querySelector('.dx-textbox, input[type=text], input');
                    if (input && node.classList.contains('canvas-node-positioned')) {
                        // Check if this node is inside a splitter panel
                        let parent = node.parentElement;
                        let inSplitter = false;
                        while (parent) {
                            if (parent.classList?.contains('lc-el-splitter-panel') || parent.classList?.contains('container-content')) {
                                inSplitter = true;
                                break;
                            }
                            parent = parent.parentElement;
                        }
                        result.push({
                            id: node.id,
                            class: node.className,
                            inSplitter: inSplitter,
                            rect: node.getBoundingClientRect().toJSON(),
                            parentClass: node.parentElement?.className?.substring(0, 80),
                        });
                    }
                }
                return result;
            }''')
            print(f"\nText boxes in canvas: {len(textbox_info)}")
            for t in textbox_info:
                print(f"  {t}")

            # Click on the text box
            if len(textbox_info) > 0:
                rect = textbox_info[0]['rect']
                page.mouse.click(rect['x'] + rect['width']/2, rect['y'] + rect['height']/2)
                page.wait_for_timeout(500)

                page.screenshot(path='test_screenshots/04_after_click.png')
                print("\nScreenshot 04 saved")

                # Check selection state
                sel_info = page.evaluate('''() => {
                    const selected = document.querySelectorAll('.canvas-node-selected');
                    return Array.from(selected).map(el => {
                        const after = getComputedStyle(el, '::after');
                        const elStyle = getComputedStyle(el);
                        return {
                            id: el.id,
                            class: el.className,
                            afterContent: after.content,
                            afterBorder: after.border,
                            afterBorderWidth: after.borderWidth,
                            afterBorderColor: after.borderColor,
                            afterDisplay: after.display,
                            afterPosition: after.position,
                            afterZIndex: after.zIndex,
                            afterWidth: after.width,
                            afterHeight: after.height,
                            afterTop: after.top,
                            afterLeft: after.left,
                            elOverflow: elStyle.overflow,
                            elPosition: elStyle.position,
                            elZIndex: elStyle.zIndex,
                            elWidth: elStyle.width,
                            elHeight: elStyle.height,
                        };
                    });
                }''')
                print(f"\nSelected nodes: {len(sel_info)}")
                print(json.dumps(sel_info, indent=2, ensure_ascii=False))

                # Check ancestor chain
                if len(sel_info) > 0:
                    ancestor_info = page.evaluate('''() => {
                        const sel = document.querySelector('.canvas-node-selected');
                        if (!sel) return [];
                        const result = [];
                        let cur = sel.parentElement;
                        while (cur && cur.tagName !== 'BODY') {
                            const s = getComputedStyle(cur);
                            result.push({
                                tag: cur.tagName,
                                class: (cur.className || '').substring(0, 100),
                                overflow: s.overflow,
                                overflowX: s.overflowX,
                                overflowY: s.overflowY,
                                zIndex: s.zIndex,
                                position: s.position,
                                contain: s.contain,
                                isolation: s.isolation,
                                transform: s.transform,
                            });
                            cur = cur.parentElement;
                        }
                        return result;
                    }''')
                    print(f"\nAncestor chain of selected node:")
                    print(json.dumps(ancestor_info, indent=2, ensure_ascii=False))

    browser.close()
    print("\nDone!")
