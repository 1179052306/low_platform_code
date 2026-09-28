"""Test selection visibility inside el-splitter-panel"""
import subprocess
import time
import sys
import json
from playwright.sync_api import sync_playwright

def run_test():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1400, "height": 900})

        page.on("console", lambda msg: print(f"[CONSOLE] {msg.type}: {msg.text}"))

        page.goto('http://localhost:5173')
        page.wait_for_load_state('networkidle')

        # Find material items
        material_items = page.locator('.material-item').all()
        print(f"Found {len(material_items)} material items")
        for i, item in enumerate(material_items):
            text = item.text_content().strip().replace('\n', ' ')
            print(f"  Item {i}: '{text}'")

        # Find el-splitter material item
        splitter_item = None
        for item in material_items:
            text = item.text_content()
            if '分割' in text or 'splitter' in text.lower():
                splitter_item = item
                break

        if not splitter_item:
            print("ERROR: Could not find splitter material item")
            browser.close()
            return

        print(f"\nFound splitter item: '{splitter_item.text_content().strip()}'")

        # Get canvas
        canvas = page.locator('.canvas-page').first
        if canvas.count() == 0:
            canvas = page.locator('[class*="canvas"]').first
        print(f"Canvas found: {canvas.count()}")

        # Drag splitter to canvas
        splitter_box = splitter_item.bounding_box()
        canvas_box = canvas.bounding_box()
        print(f"Splitter box: {splitter_box}")
        print(f"Canvas box: {canvas_box}")

        page.mouse.move(splitter_box['x'] + splitter_box['width']/2, splitter_box['y'] + splitter_box['height']/2)
        page.mouse.down()
        page.wait_for_timeout(300)
        page.mouse.move(canvas_box['x'] + 300, canvas_box['y'] + 200, steps=15)
        page.wait_for_timeout(300)
        page.mouse.up()
        page.wait_for_timeout(1000)

        page.screenshot(path='test_screenshots/02_after_splitter.png')
        print("Screenshot 02 saved")

        # Find empty container inside splitter panel
        empty_containers = page.locator('text=拖入组件到此容器').all()
        print(f"Empty containers: {len(empty_containers)}")

        if len(empty_containers) == 0:
            # Maybe the text is different, try to find any el-splitter-panel
            empty_containers = page.locator('.lc-el-splitter-panel').all()
            print(f"Splitter panels (by class): {len(empty_containers)}")

        if len(empty_containers) > 0:
            panel = empty_containers[0]
            panel_box = panel.bounding_box()
            print(f"Panel box: {panel_box}")

            # Find a text box / input material item
            textbox_item = None
            for item in material_items:
                text = item.text_content()
                if '文本' in text or '输入' in text or '单行' in text:
                    textbox_item = item
                    break

            if not textbox_item and len(material_items) > 3:
                textbox_item = material_items[3]

            if textbox_item:
                tb_box = textbox_item.bounding_box()
                print(f"Text box item: '{textbox_item.text_content().strip()}'")
                print(f"Text box item box: {tb_box}")

                # Drag text box into splitter panel
                page.mouse.move(tb_box['x'] + tb_box['width']/2, tb_box['y'] + tb_box['height']/2)
                page.mouse.down()
                page.wait_for_timeout(300)
                page.mouse.move(panel_box['x'] + panel_box['width']/4, panel_box['y'] + panel_box['height']/4, steps=15)
                page.wait_for_timeout(300)
                page.mouse.up()
                page.wait_for_timeout(1000)

                page.screenshot(path='test_screenshots/03_after_textbox_drop.png')
                print("Screenshot 03 saved")

                # Now click on the text box inside the panel
                # Find the dx textbox
                dx_input = page.locator('.dx-textbox').first
                if dx_input.count() == 0:
                    dx_input = page.locator('input').first
                if dx_input.count() == 0:
                    # Try clicking the canvas-node inside the panel
                    dx_input = page.locator('.canvas-node-positioned .canvas-node').first

                print(f"Input element found: {dx_input.count()}")

                if dx_input.count() > 0:
                    input_box = dx_input.bounding_box()
                    print(f"Input box: {input_box}")

                    if input_box:
                        # Click on it
                        page.mouse.click(input_box['x'] + input_box['width']/2, input_box['y'] + input_box['height']/2)
                        page.wait_for_timeout(500)

                        page.screenshot(path='test_screenshots/04_after_click.png')
                        print("Screenshot 04 saved")

                        # Check selection
                        selected_nodes = page.locator('.canvas-node-selected').all()
                        print(f"\nSelected nodes: {len(selected_nodes)}")

                        for i, node in enumerate(selected_nodes):
                            classes = node.get_attribute('class')
                            print(f"\n  Node {i} classes: {classes}")

                            # Check ::after
                            after_info = page.evaluate('''(el) => {
                                const s = getComputedStyle(el, '::after');
                                return {
                                    content: s.content,
                                    position: s.position,
                                    border: s.border,
                                    display: s.display,
                                    zIndex: s.zIndex,
                                    width: s.width,
                                    height: s.height,
                                    top: s.top,
                                    left: s.left,
                                    right: s.right,
                                    bottom: s.bottom,
                                };
                            }''', node.element_handle())
                            print(f"  ::after: {json.dumps(after_info, indent=4)}")

                            # Check element computed style
                            el_info = page.evaluate('''(el) => {
                                const s = getComputedStyle(el);
                                return {
                                    position: s.position,
                                    overflow: s.overflow,
                                    zIndex: s.zIndex,
                                    width: s.width,
                                    height: s.height,
                                };
                            }''', node.element_handle())
                            print(f"  Element: {json.dumps(el_info, indent=4)}")

                        # Check ancestor chain for overflow/clipping
                        ancestors = page.evaluate('''(el) => {
                            const result = [];
                            let cur = el.parentElement;
                            while (cur && cur.tagName !== 'BODY') {
                                const s = getComputedStyle(cur);
                                result.push({
                                    tag: cur.tagName,
                                    class: cur.className.substring(0, 80),
                                    overflow: s.overflow,
                                    overflowX: s.overflowX,
                                    overflowY: s.overflowY,
                                    zIndex: s.zIndex,
                                    position: s.position,
                                    contain: s.contain,
                                });
                                cur = cur.parentElement;
                            }
                            return result;
                        }''', dx_input.first.element_handle())
                        print(f"\nAncestor chain:")
                        print(json.dumps(ancestors, indent=2))

        browser.close()
        print("\nDone!")

if __name__ == '__main__':
    run_test()
