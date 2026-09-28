from playwright.sync_api import sync_playwright
import time

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 900})

    # Navigate to the dev server
    page.goto('http://localhost:5175')
    page.wait_for_load_state('networkidle')
    time.sleep(1)

    # Take initial screenshot
    page.screenshot(path='/tmp/initial.png')
    print("Initial screenshot taken")

    # Find the el-splitter in the component library
    # Look for material items with text "分割面板"
    splitter_item = page.locator('text=分割面板').first
    if splitter_item.count() == 0:
        # Try finding by draggable attribute
        print("Looking for splitter in material panel...")
        # Let's check the page content first
        content = page.content()
        if '分割面板' in content:
            print("Found '分割面板' text in page")
        else:
            print("'分割面板' text NOT found in page")
        # Try to find material items
        materials = page.locator('.material-item, [draggable="true"]')
        print(f"Found {materials.count()} draggable elements")
        for i in range(min(materials.count(), 20)):
            text = materials.nth(i).inner_text()
            print(f"  Material {i}: {text}")
        browser.close()
        exit()

    print(f"Found splitter item: {splitter_item.inner_text()}")

    # Get the splitter item's bounding box
    splitter_box = splitter_item.bounding_box()
    print(f"Splitter item box: {splitter_box}")

    # Get the canvas area
    canvas = page.locator('.canvas-page, .canvas-scroll, [class*="canvas"]').first
    canvas_box = canvas.bounding_box()
    print(f"Canvas box: {canvas_box}")

    # Drag the splitter to the canvas
    if splitter_box and canvas_box:
        # Use drag and drop
        page.mouse.move(splitter_box['x'] + splitter_box['width'] / 2, splitter_box['y'] + splitter_box['height'] / 2)
        page.mouse.down()
        time.sleep(0.2)
        # Move to canvas center
        target_x = canvas_box['x'] + 200
        target_y = canvas_box['y'] + 200
        page.mouse.move(target_x, target_y, steps=10)
        time.sleep(0.2)
        page.mouse.up()
        time.sleep(1)

    # Take screenshot after drag
    page.screenshot(path='/tmp/after_drag.png')
    print("Screenshot after drag taken")

    # Find the el-splitter on canvas
    splitter_node = page.locator('.canvas-node-selected').first
    if splitter_node.count() == 0:
        # Try clicking on the splitter we just dropped
        nodes = page.locator('.canvas-node')
        print(f"Found {nodes.count()} canvas nodes")
        for i in range(nodes.count()):
            node = nodes.nth(i)
            text = node.inner_text()
            print(f"  Node {i}: {text[:50]}")
        # Try to find the splitter by its content
        splitter_node = page.locator('.canvas-node:has(.lc-splitter)').first

    if splitter_node.count() == 0:
        print("Could not find splitter node on canvas")
        browser.close()
        exit()

    # Click to select it
    splitter_node.click()
    time.sleep(0.5)

    # Get the selected node
    selected = page.locator('.canvas-node-selected').first
    box = selected.bounding_box()
    print(f"Selected node box: {box}")

    if box:
        # Find the south resize handle (bottom center)
        south_handle_x = box['x'] + box['width'] / 2
        south_handle_y = box['y'] + box['height']

        print(f"South handle position: ({south_handle_x}, {south_handle_y})")

        # Resize height: drag south handle down by 100px
        page.mouse.move(south_handle_x, south_handle_y)
        page.mouse.down()
        time.sleep(0.1)
        page.mouse.move(south_handle_x, south_handle_y + 100, steps=5)
        time.sleep(0.1)
        page.mouse.up()
        time.sleep(0.5)

        # Take screenshot after height resize
        page.screenshot(path='/tmp/after_height_resize.png')
        print("Screenshot after height resize taken")

        # Check the node dimensions after height resize
        box_after_height = selected.bounding_box()
        print(f"Box after height resize: {box_after_height}")

        # Now resize width: drag east handle right by 100px
        east_handle_x = box['x'] + box['width']
        east_handle_y = box['y'] + box['height'] / 2

        print(f"East handle position: ({east_handle_x}, {east_handle_y})")

        page.mouse.move(east_handle_x, east_handle_y)
        page.mouse.down()
        time.sleep(0.1)
        page.mouse.move(east_handle_x + 100, east_handle_y, steps=5)
        time.sleep(0.1)
        page.mouse.up()
        time.sleep(0.5)

        # Take screenshot after width resize
        page.screenshot(path='/tmp/after_width_resize.png')
        print("Screenshot after width resize taken")

        # Check the node dimensions after width resize
        box_after_width = selected.bounding_box()
        print(f"Box after width resize: {box_after_width}")

        # Check the inner content dimensions
        inner = selected.locator('.lc-splitter, .lc-tabs').first
        if inner.count() > 0:
            inner_box = inner.bounding_box()
            print(f"Inner content box: {inner_box}")
            if inner_box and box_after_width:
                width_diff = box_after_width['width'] - inner_box['width']
                print(f"Width difference (wrapper - inner): {width_diff}px")
                if abs(width_diff) > 2:
                    print("ISSUE DETECTED: Inner content width doesn't match wrapper width!")
                else:
                    print("No width mismatch detected")
        else:
            print("Could not find inner content element")

    browser.close()
    print("Test complete")
