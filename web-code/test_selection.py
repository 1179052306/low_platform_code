from playwright.sync_api import sync_playwright
import json

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 900})

    # Capture console messages
    page.on("console", lambda msg: print(f"[CONSOLE] {msg.type}: {msg.text}"))

    page.goto('http://localhost:5173')
    page.wait_for_load_state('networkidle')

    # Take initial screenshot
    page.screenshot(path='test_screenshots/01_initial.png', full_page=True)

    # Find the material panel - look for splitter in the material list
    # First, let's see what's on the page
    content = page.content()

    # Look for material items that contain "分割" or "splitter"
    splitter_items = page.locator('text=/分割|splitter/i').all()
    print(f"Found {len(splitter_items)} splitter-related elements")

    # Let's try to drag a splitter to the canvas
    # First find the material panel items
    material_items = page.locator('.material-item, [draggable="true"]').all()
    print(f"Found {len(material_items)} draggable items")

    # Print text of each material item
    for i, item in enumerate(material_items):
        text = item.text_content()
        print(f"  Item {i}: {text}")

    # Find el-splitter in material panel
    splitter_item = page.locator('.material-item:has-text("分割")').first
    if splitter_item.count() == 0:
        splitter_item = page.locator('[draggable="true"]:has-text("分割")').first

    print(f"Splitter item found: {splitter_item.count()}")

    if splitter_item.count() > 0:
        # Get canvas area
        canvas = page.locator('.canvas-page, .canvas-scroll, #canvas').first
        print(f"Canvas found: {canvas.count()}")

        # Drag splitter to canvas
        splitter_box = splitter_item.bounding_box()
        canvas_box = canvas.bounding_box()

        if splitter_box and canvas_box:
            print(f"Splitter box: {splitter_box}")
            print(f"Canvas box: {canvas_box}")

            # Drag and drop
            page.mouse.move(splitter_box['x'] + splitter_box['width']/2, splitter_box['y'] + splitter_box['height']/2)
            page.mouse.down()
            page.wait_for_timeout(200)
            page.mouse.move(canvas_box['x'] + 200, canvas_box['y'] + 200, steps=10)
            page.wait_for_timeout(200)
            page.mouse.up()
            page.wait_for_timeout(500)

        page.screenshot(path='test_screenshots/02_after_splitter_drop.png', full_page=True)

    # Now try to drag a text box into the splitter panel
    textbox_item = page.locator('.material-item:has-text("文本"), [draggable="true"]:has-text("文本")').first
    if textbox_item.count() == 0:
        textbox_item = page.locator('.material-item:has-text("单行"), [draggable="true"]:has-text("单行")').first
    if textbox_item.count() == 0:
        # Try to find any form input item
        textbox_item = page.locator('.material-item').nth(3)

    print(f"Text box item found: {textbox_item.count()}")

    if textbox_item.count() > 0:
        # Find the splitter panel inside the canvas
        splitter_panel = page.locator('.canvas-node:has-text("拖入组件到此容器")').first
        print(f"Splitter panel (empty container) found: {splitter_panel.count()}")

        if splitter_panel.count() > 0:
            panel_box = splitter_panel.bounding_box()
            textbox_box = textbox_item.bounding_box()

            if panel_box and textbox_box:
                print(f"Panel box: {panel_box}")
                print(f"Text box item box: {textbox_box}")

                # Drag text box into splitter panel
                page.mouse.move(textbox_box['x'] + textbox_box['width']/2, textbox_box['y'] + textbox_box['height']/2)
                page.mouse.down()
                page.wait_for_timeout(200)
                page.mouse.move(panel_box['x'] + panel_box['width']/2, panel_box['y'] + panel_box['height']/2, steps=10)
                page.wait_for_timeout(200)
                page.mouse.up()
                page.wait_for_timeout(500)

        page.screenshot(path='test_screenshots/03_after_textbox_drop.png', full_page=True)

    # Now click on the text box to select it
    # Find the text box inside the splitter panel
    textbox_in_panel = page.locator('.canvas-node .dx-textbox, .canvas-node input').first
    if textbox_in_panel.count() == 0:
        textbox_in_panel = page.locator('.canvas-node-positioned .canvas-node').first

    print(f"Text box in panel found: {textbox_in_panel.count()}")

    if textbox_in_panel.count() > 0:
        box = textbox_in_panel.bounding_box()
        if box:
            # Click on the text box
            page.mouse.click(box['x'] + box['width']/2, box['y'] + box['height']/2)
            page.wait_for_timeout(500)

        page.screenshot(path='test_screenshots/04_after_click_textbox.png', full_page=True)

        # Check if the canvas-node has the selected class
        selected_nodes = page.locator('.canvas-node-selected').all()
        print(f"Selected nodes count: {len(selected_nodes)}")

        for i, node in enumerate(selected_nodes):
            classes = node.get_attribute('class')
            print(f"  Selected node {i} classes: {classes}")

            # Check ::after pseudo element
            after_style = page.evaluate('''(el) => {
                const styles = window.getComputedStyle(el, '::after');
                return {
                    content: styles.getPropertyValue('content'),
                    position: styles.getPropertyValue('position'),
                    border: styles.getPropertyValue('border'),
                    display: styles.getPropertyValue('display'),
                    zIndex: styles.getPropertyValue('z-index'),
                    width: styles.getPropertyValue('width'),
                    height: styles.getPropertyValue('height'),
                    top: styles.getPropertyValue('top'),
                    left: styles.getPropertyValue('left'),
                };
            }''', node.element_handle())
            print(f"  ::after style: {json.dumps(after_style, indent=2)}")

            # Also check the element's own style
            el_style = page.evaluate('''(el) => {
                const styles = window.getComputedStyle(el);
                return {
                    position: styles.getPropertyValue('position'),
                    overflow: styles.getPropertyValue('overflow'),
                    zIndex: styles.getPropertyValue('z-index'),
                    width: styles.getPropertyValue('width'),
                    height: styles.getPropertyValue('height'),
                };
            }''', node.element_handle())
            print(f"  Element style: {json.dumps(el_style, indent=2)}")

    # Also check ancestor overflow
    if textbox_in_panel.count() > 0:
        ancestor_info = page.evaluate('''(el) => {
            const result = [];
            let current = el.parentElement;
            while (current && current.tagName !== 'BODY') {
                const styles = window.getComputedStyle(current);
                if (styles.overflow !== 'visible' || styles.zIndex !== 'auto') {
                    result.push({
                        tag: current.tagName,
                        class: current.className,
                        overflow: styles.overflow,
                        overflowX: styles.overflowX,
                        overflowY: styles.overflowY,
                        zIndex: styles.zIndex,
                        position: styles.position,
                    });
                }
                current = current.parentElement;
            }
            return result;
        }''', textbox_in_panel.first.element_handle())
        print(f"\nAncestor overflow/z-index chain:")
        print(json.dumps(ancestor_info, indent=2))

    browser.close()
    print("\nDone!")
