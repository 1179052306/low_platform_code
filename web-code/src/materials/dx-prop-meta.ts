/**
 * 由 scripts/gen-dx-props.mjs 自动生成，请勿手动编辑。
 * 每个 DevExtreme 物料的全量可配置属性（不含事件回调）。
 */
export type DxPropKind = 'string' | 'number' | 'boolean' | 'select' | 'json'

export interface DxPropMeta {
  name: string
  kind: DxPropKind
  options?: { label: string; value: string }[]
}

export const dxPropMeta: Record<string, DxPropMeta[]> = {
  "dx-chart": [
    {
      name: "adaptiveLayout",
      kind: "json"
    },
    {
      name: "adjustOnZoom",
      kind: "boolean"
    },
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "annotations",
      kind: "json"
    },
    {
      name: "argumentAxis",
      kind: "json"
    },
    {
      name: "autoHidePointMarkers",
      kind: "boolean"
    },
    {
      name: "barGroupPadding",
      kind: "number"
    },
    {
      name: "barGroupWidth",
      kind: "number"
    },
    {
      name: "commonAnnotationSettings",
      kind: "json"
    },
    {
      name: "commonAxisSettings",
      kind: "json"
    },
    {
      name: "commonPaneSettings",
      kind: "json"
    },
    {
      name: "commonSeriesSettings",
      kind: "json"
    },
    {
      name: "containerBackgroundColor",
      kind: "string"
    },
    {
      name: "crosshair",
      kind: "json"
    },
    {
      name: "dataPrepareSettings",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "defaultPane",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "legend",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "maxBubbleSize",
      kind: "number"
    },
    {
      name: "minBubbleSize",
      kind: "number"
    },
    {
      name: "negativesAsZeroes",
      kind: "boolean"
    },
    {
      name: "palette",
      kind: "json"
    },
    {
      name: "paletteExtensionMode",
      kind: "select",
      'options': [
        {
          "label": "alternate",
          "value": "alternate"
        },
        {
          "label": "blend",
          "value": "blend"
        },
        {
          "label": "extrapolate",
          "value": "extrapolate"
        }
      ]
    },
    {
      name: "panes",
      kind: "json"
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "pointSelectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "resizePanesOnZoom",
      kind: "boolean"
    },
    {
      name: "resolveLabelOverlapping",
      kind: "select",
      'options': [
        {
          "label": "hide",
          "value": "hide"
        },
        {
          "label": "none",
          "value": "none"
        },
        {
          "label": "stack",
          "value": "stack"
        }
      ]
    },
    {
      name: "rotated",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrollBar",
      kind: "json"
    },
    {
      name: "series",
      kind: "json"
    },
    {
      name: "seriesSelectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "seriesTemplate",
      kind: "json"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "stickyHovering",
      kind: "boolean"
    },
    {
      name: "synchronizeMultiAxes",
      kind: "boolean"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "valueAxis",
      kind: "json"
    },
    {
      name: "zoomAndPan",
      kind: "json"
    }
  ],
  "dx-pie-chart": [
    {
      name: "adaptiveLayout",
      kind: "json"
    },
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "annotations",
      kind: "json"
    },
    {
      name: "centerTemplate",
      kind: "json"
    },
    {
      name: "commonAnnotationSettings",
      kind: "json"
    },
    {
      name: "commonSeriesSettings",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "diameter",
      kind: "number"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "innerRadius",
      kind: "number"
    },
    {
      name: "legend",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "minDiameter",
      kind: "number"
    },
    {
      name: "palette",
      kind: "json"
    },
    {
      name: "paletteExtensionMode",
      kind: "select",
      'options': [
        {
          "label": "alternate",
          "value": "alternate"
        },
        {
          "label": "blend",
          "value": "blend"
        },
        {
          "label": "extrapolate",
          "value": "extrapolate"
        }
      ]
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "pointSelectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "resolveLabelOverlapping",
      kind: "select",
      'options': [
        {
          "label": "hide",
          "value": "hide"
        },
        {
          "label": "none",
          "value": "none"
        },
        {
          "label": "shift",
          "value": "shift"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "segmentsDirection",
      kind: "select",
      'options': [
        {
          "label": "anticlockwise",
          "value": "anticlockwise"
        },
        {
          "label": "clockwise",
          "value": "clockwise"
        }
      ]
    },
    {
      name: "series",
      kind: "json"
    },
    {
      name: "seriesTemplate",
      kind: "json"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "sizeGroup",
      kind: "string"
    },
    {
      name: "startAngle",
      kind: "number"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "type",
      kind: "select",
      'options': [
        {
          "label": "donut",
          "value": "donut"
        },
        {
          "label": "doughnut",
          "value": "doughnut"
        },
        {
          "label": "pie",
          "value": "pie"
        }
      ]
    }
  ],
  "dx-polar-chart": [
    {
      name: "adaptiveLayout",
      kind: "json"
    },
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "annotations",
      kind: "json"
    },
    {
      name: "argumentAxis",
      kind: "json"
    },
    {
      name: "barGroupPadding",
      kind: "number"
    },
    {
      name: "barGroupWidth",
      kind: "number"
    },
    {
      name: "commonAnnotationSettings",
      kind: "json"
    },
    {
      name: "commonAxisSettings",
      kind: "json"
    },
    {
      name: "commonSeriesSettings",
      kind: "json"
    },
    {
      name: "containerBackgroundColor",
      kind: "string"
    },
    {
      name: "dataPrepareSettings",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "legend",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "negativesAsZeroes",
      kind: "boolean"
    },
    {
      name: "palette",
      kind: "json"
    },
    {
      name: "paletteExtensionMode",
      kind: "select",
      'options': [
        {
          "label": "alternate",
          "value": "alternate"
        },
        {
          "label": "blend",
          "value": "blend"
        },
        {
          "label": "extrapolate",
          "value": "extrapolate"
        }
      ]
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "pointSelectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "resolveLabelOverlapping",
      kind: "select",
      'options': [
        {
          "label": "hide",
          "value": "hide"
        },
        {
          "label": "none",
          "value": "none"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "series",
      kind: "json"
    },
    {
      name: "seriesSelectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "seriesTemplate",
      kind: "json"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "useSpiderWeb",
      kind: "boolean"
    },
    {
      name: "valueAxis",
      kind: "json"
    }
  ],
  "dx-bar-gauge": [
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "backgroundColor",
      kind: "string"
    },
    {
      name: "barSpacing",
      kind: "number"
    },
    {
      name: "baseValue",
      kind: "number"
    },
    {
      name: "centerTemplate",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "endValue",
      kind: "number"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "geometry",
      kind: "json"
    },
    {
      name: "label",
      kind: "json"
    },
    {
      name: "legend",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "palette",
      kind: "json"
    },
    {
      name: "paletteExtensionMode",
      kind: "select",
      'options': [
        {
          "label": "alternate",
          "value": "alternate"
        },
        {
          "label": "blend",
          "value": "blend"
        },
        {
          "label": "extrapolate",
          "value": "extrapolate"
        }
      ]
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "relativeInnerRadius",
      kind: "number"
    },
    {
      name: "resolveLabelOverlapping",
      kind: "select",
      'options': [
        {
          "label": "hide",
          "value": "hide"
        },
        {
          "label": "none",
          "value": "none"
        },
        {
          "label": "shift",
          "value": "shift"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "startValue",
      kind: "number"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "values",
      kind: "json"
    }
  ],
  "dx-bullet": [
    {
      name: "color",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "endScaleValue",
      kind: "number"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showTarget",
      kind: "boolean"
    },
    {
      name: "showZeroLevel",
      kind: "boolean"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "startScaleValue",
      kind: "number"
    },
    {
      name: "target",
      kind: "number"
    },
    {
      name: "targetColor",
      kind: "string"
    },
    {
      name: "targetWidth",
      kind: "number"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "value",
      kind: "number"
    }
  ],
  "dx-circular-gauge": [
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "centerTemplate",
      kind: "json"
    },
    {
      name: "containerBackgroundColor",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "geometry",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "rangeContainer",
      kind: "json"
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scale",
      kind: "json"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "subvalueIndicator",
      kind: "json"
    },
    {
      name: "subvalues",
      kind: "json"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "value",
      kind: "number"
    },
    {
      name: "valueIndicator",
      kind: "json"
    }
  ],
  "dx-linear-gauge": [
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "containerBackgroundColor",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "geometry",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "rangeContainer",
      kind: "json"
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scale",
      kind: "json"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "subvalueIndicator",
      kind: "json"
    },
    {
      name: "subvalues",
      kind: "json"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "value",
      kind: "number"
    },
    {
      name: "valueIndicator",
      kind: "json"
    }
  ],
  "dx-funnel": [
    {
      name: "adaptiveLayout",
      kind: "json"
    },
    {
      name: "algorithm",
      kind: "select",
      'options': [
        {
          "label": "dynamicHeight",
          "value": "dynamicHeight"
        },
        {
          "label": "dynamicSlope",
          "value": "dynamicSlope"
        }
      ]
    },
    {
      name: "argumentField",
      kind: "string"
    },
    {
      name: "colorField",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "hoverEnabled",
      kind: "boolean"
    },
    {
      name: "inverted",
      kind: "boolean"
    },
    {
      name: "item",
      kind: "json"
    },
    {
      name: "label",
      kind: "json"
    },
    {
      name: "legend",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "neckHeight",
      kind: "number"
    },
    {
      name: "neckWidth",
      kind: "number"
    },
    {
      name: "palette",
      kind: "json"
    },
    {
      name: "paletteExtensionMode",
      kind: "select",
      'options': [
        {
          "label": "alternate",
          "value": "alternate"
        },
        {
          "label": "blend",
          "value": "blend"
        },
        {
          "label": "extrapolate",
          "value": "extrapolate"
        }
      ]
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "resolveLabelOverlapping",
      kind: "select",
      'options': [
        {
          "label": "hide",
          "value": "hide"
        },
        {
          "label": "none",
          "value": "none"
        },
        {
          "label": "shift",
          "value": "shift"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        },
        {
          "label": "none",
          "value": "none"
        }
      ]
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "sortData",
      kind: "boolean"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "valueField",
      kind: "string"
    }
  ],
  "dx-sankey": [
    {
      name: "adaptiveLayout",
      kind: "json"
    },
    {
      name: "alignment",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "hoverEnabled",
      kind: "boolean"
    },
    {
      name: "label",
      kind: "json"
    },
    {
      name: "link",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "node",
      kind: "json"
    },
    {
      name: "palette",
      kind: "json"
    },
    {
      name: "paletteExtensionMode",
      kind: "select",
      'options': [
        {
          "label": "alternate",
          "value": "alternate"
        },
        {
          "label": "blend",
          "value": "blend"
        },
        {
          "label": "extrapolate",
          "value": "extrapolate"
        }
      ]
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "sortData",
      kind: "json"
    },
    {
      name: "sourceField",
      kind: "string"
    },
    {
      name: "targetField",
      kind: "string"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "weightField",
      kind: "string"
    }
  ],
  "dx-sparkline": [
    {
      name: "argumentField",
      kind: "string"
    },
    {
      name: "barNegativeColor",
      kind: "string"
    },
    {
      name: "barPositiveColor",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "firstLastColor",
      kind: "string"
    },
    {
      name: "ignoreEmptyPoints",
      kind: "boolean"
    },
    {
      name: "lineColor",
      kind: "string"
    },
    {
      name: "lineWidth",
      kind: "number"
    },
    {
      name: "lossColor",
      kind: "string"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "maxColor",
      kind: "string"
    },
    {
      name: "maxValue",
      kind: "number"
    },
    {
      name: "minColor",
      kind: "string"
    },
    {
      name: "minValue",
      kind: "number"
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "pointColor",
      kind: "string"
    },
    {
      name: "pointSize",
      kind: "number"
    },
    {
      name: "pointSymbol",
      kind: "select",
      'options': [
        {
          "label": "circle",
          "value": "circle"
        },
        {
          "label": "cross",
          "value": "cross"
        },
        {
          "label": "polygon",
          "value": "polygon"
        },
        {
          "label": "square",
          "value": "square"
        },
        {
          "label": "triangle",
          "value": "triangle"
        },
        {
          "label": "triangleDown",
          "value": "triangleDown"
        },
        {
          "label": "triangleUp",
          "value": "triangleUp"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showFirstLast",
      kind: "boolean"
    },
    {
      name: "showMinMax",
      kind: "boolean"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "type",
      kind: "select",
      'options': [
        {
          "label": "area",
          "value": "area"
        },
        {
          "label": "bar",
          "value": "bar"
        },
        {
          "label": "line",
          "value": "line"
        },
        {
          "label": "spline",
          "value": "spline"
        },
        {
          "label": "splinearea",
          "value": "splinearea"
        },
        {
          "label": "steparea",
          "value": "steparea"
        },
        {
          "label": "stepline",
          "value": "stepline"
        },
        {
          "label": "winloss",
          "value": "winloss"
        }
      ]
    },
    {
      name: "valueField",
      kind: "string"
    },
    {
      name: "winColor",
      kind: "string"
    },
    {
      name: "winlossThreshold",
      kind: "number"
    }
  ],
  "dx-tree-map": [
    {
      name: "childrenField",
      kind: "string"
    },
    {
      name: "colorField",
      kind: "string"
    },
    {
      name: "colorizer",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "group",
      kind: "json"
    },
    {
      name: "hoverEnabled",
      kind: "boolean"
    },
    {
      name: "idField",
      kind: "string"
    },
    {
      name: "interactWithGroup",
      kind: "boolean"
    },
    {
      name: "labelField",
      kind: "string"
    },
    {
      name: "layoutAlgorithm",
      kind: "json"
    },
    {
      name: "layoutDirection",
      kind: "select",
      'options': [
        {
          "label": "leftBottomRightTop",
          "value": "leftBottomRightTop"
        },
        {
          "label": "leftTopRightBottom",
          "value": "leftTopRightBottom"
        },
        {
          "label": "rightBottomLeftTop",
          "value": "rightBottomLeftTop"
        },
        {
          "label": "rightTopLeftBottom",
          "value": "rightTopLeftBottom"
        }
      ]
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "maxDepth",
      kind: "number"
    },
    {
      name: "parentField",
      kind: "string"
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        },
        {
          "label": "none",
          "value": "none"
        }
      ]
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "tile",
      kind: "json"
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "valueField",
      kind: "string"
    }
  ],
  "dx-range-selector": [
    {
      name: "background",
      kind: "json"
    },
    {
      name: "behavior",
      kind: "json"
    },
    {
      name: "chart",
      kind: "json"
    },
    {
      name: "containerBackgroundColor",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "dataSourceField",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "indent",
      kind: "json"
    },
    {
      name: "loadingIndicator",
      kind: "json"
    },
    {
      name: "margin",
      kind: "json"
    },
    {
      name: "pathModified",
      kind: "boolean"
    },
    {
      name: "redrawOnResize",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scale",
      kind: "json"
    },
    {
      name: "selectedRangeColor",
      kind: "string"
    },
    {
      name: "selectedRangeUpdateMode",
      kind: "select",
      'options': [
        {
          "label": "auto",
          "value": "auto"
        },
        {
          "label": "keep",
          "value": "keep"
        },
        {
          "label": "reset",
          "value": "reset"
        },
        {
          "label": "shift",
          "value": "shift"
        }
      ]
    },
    {
      name: "shutter",
      kind: "json"
    },
    {
      name: "size",
      kind: "json"
    },
    {
      name: "sliderHandle",
      kind: "json"
    },
    {
      name: "sliderMarker",
      kind: "json"
    },
    {
      name: "theme",
      kind: "select",
      'options': [
        {
          "label": "generic.dark",
          "value": "generic.dark"
        },
        {
          "label": "generic.light",
          "value": "generic.light"
        },
        {
          "label": "generic.contrast",
          "value": "generic.contrast"
        },
        {
          "label": "generic.carmine",
          "value": "generic.carmine"
        },
        {
          "label": "generic.darkmoon",
          "value": "generic.darkmoon"
        },
        {
          "label": "generic.darkviolet",
          "value": "generic.darkviolet"
        },
        {
          "label": "generic.greenmist",
          "value": "generic.greenmist"
        },
        {
          "label": "generic.softblue",
          "value": "generic.softblue"
        },
        {
          "label": "material.blue.light",
          "value": "material.blue.light"
        },
        {
          "label": "material.lime.light",
          "value": "material.lime.light"
        },
        {
          "label": "material.orange.light",
          "value": "material.orange.light"
        },
        {
          "label": "material.purple.light",
          "value": "material.purple.light"
        },
        {
          "label": "material.teal.light",
          "value": "material.teal.light"
        }
      ]
    },
    {
      name: "title",
      kind: "json"
    },
    {
      name: "value",
      kind: "json"
    }
  ],
  "dx-data-grid": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowColumnReordering",
      kind: "boolean"
    },
    {
      name: "allowColumnResizing",
      kind: "boolean"
    },
    {
      name: "autoNavigateToFocusedRow",
      kind: "boolean"
    },
    {
      name: "cacheEnabled",
      kind: "boolean"
    },
    {
      name: "cellHintEnabled",
      kind: "boolean"
    },
    {
      name: "columnAutoWidth",
      kind: "boolean"
    },
    {
      name: "columnChooser",
      kind: "json"
    },
    {
      name: "columnFixing",
      kind: "json"
    },
    {
      name: "columnHidingEnabled",
      kind: "boolean"
    },
    {
      name: "columnMinWidth",
      kind: "number"
    },
    {
      name: "columnResizingMode",
      kind: "select",
      'options': [
        {
          "label": "nextColumn",
          "value": "nextColumn"
        },
        {
          "label": "widget",
          "value": "widget"
        }
      ]
    },
    {
      name: "columns",
      kind: "json"
    },
    {
      name: "columnWidth",
      kind: "json"
    },
    {
      name: "dataRowTemplate",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "dateSerializationFormat",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "editing",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "errorRowEnabled",
      kind: "boolean"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "filterBuilder",
      kind: "json"
    },
    {
      name: "filterBuilderPopup",
      kind: "json"
    },
    {
      name: "filterPanel",
      kind: "json"
    },
    {
      name: "filterRow",
      kind: "json"
    },
    {
      name: "filterSyncEnabled",
      kind: "json"
    },
    {
      name: "filterValue",
      kind: "json"
    },
    {
      name: "focusedColumnIndex",
      kind: "number"
    },
    {
      name: "focusedRowEnabled",
      kind: "boolean"
    },
    {
      name: "focusedRowIndex",
      kind: "number"
    },
    {
      name: "focusedRowKey",
      kind: "json"
    },
    {
      name: "grouping",
      kind: "json"
    },
    {
      name: "groupPanel",
      kind: "json"
    },
    {
      name: "headerFilter",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "highlightChanges",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "keyboardNavigation",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "json"
    },
    {
      name: "loadPanel",
      kind: "json"
    },
    {
      name: "masterDetail",
      kind: "json"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "pager",
      kind: "json"
    },
    {
      name: "paging",
      kind: "json"
    },
    {
      name: "remoteOperations",
      kind: "json"
    },
    {
      name: "renderAsync",
      kind: "boolean"
    },
    {
      name: "repaintChangesOnly",
      kind: "boolean"
    },
    {
      name: "rowAlternationEnabled",
      kind: "boolean"
    },
    {
      name: "rowDragging",
      kind: "json"
    },
    {
      name: "rowTemplate",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrolling",
      kind: "json"
    },
    {
      name: "searchPanel",
      kind: "json"
    },
    {
      name: "selectedRowKeys",
      kind: "json"
    },
    {
      name: "selection",
      kind: "json"
    },
    {
      name: "selectionFilter",
      kind: "json"
    },
    {
      name: "showBorders",
      kind: "boolean"
    },
    {
      name: "showColumnHeaders",
      kind: "boolean"
    },
    {
      name: "showColumnLines",
      kind: "boolean"
    },
    {
      name: "showRowLines",
      kind: "boolean"
    },
    {
      name: "sortByGroupSummaryInfo",
      kind: "json"
    },
    {
      name: "sorting",
      kind: "json"
    },
    {
      name: "stateStoring",
      kind: "json"
    },
    {
      name: "summary",
      kind: "json"
    },
    {
      name: "syncLookupFilterValues",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "toolbar",
      kind: "json"
    },
    {
      name: "twoWayBindingEnabled",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wordWrapEnabled",
      kind: "boolean"
    }
  ],
  "dx-tree-list": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowColumnReordering",
      kind: "boolean"
    },
    {
      name: "allowColumnResizing",
      kind: "boolean"
    },
    {
      name: "autoExpandAll",
      kind: "boolean"
    },
    {
      name: "autoNavigateToFocusedRow",
      kind: "boolean"
    },
    {
      name: "cacheEnabled",
      kind: "boolean"
    },
    {
      name: "cellHintEnabled",
      kind: "boolean"
    },
    {
      name: "columnAutoWidth",
      kind: "boolean"
    },
    {
      name: "columnChooser",
      kind: "json"
    },
    {
      name: "columnFixing",
      kind: "json"
    },
    {
      name: "columnHidingEnabled",
      kind: "boolean"
    },
    {
      name: "columnMinWidth",
      kind: "number"
    },
    {
      name: "columnResizingMode",
      kind: "select",
      'options': [
        {
          "label": "nextColumn",
          "value": "nextColumn"
        },
        {
          "label": "widget",
          "value": "widget"
        }
      ]
    },
    {
      name: "columns",
      kind: "json"
    },
    {
      name: "columnWidth",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "dataStructure",
      kind: "select",
      'options': [
        {
          "label": "plain",
          "value": "plain"
        },
        {
          "label": "tree",
          "value": "tree"
        }
      ]
    },
    {
      name: "dateSerializationFormat",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "editing",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "errorRowEnabled",
      kind: "boolean"
    },
    {
      name: "expandedRowKeys",
      kind: "json"
    },
    {
      name: "expandNodesOnFiltering",
      kind: "boolean"
    },
    {
      name: "filterBuilder",
      kind: "json"
    },
    {
      name: "filterBuilderPopup",
      kind: "json"
    },
    {
      name: "filterMode",
      kind: "select",
      'options': [
        {
          "label": "fullBranch",
          "value": "fullBranch"
        },
        {
          "label": "withAncestors",
          "value": "withAncestors"
        },
        {
          "label": "matchOnly",
          "value": "matchOnly"
        }
      ]
    },
    {
      name: "filterPanel",
      kind: "json"
    },
    {
      name: "filterRow",
      kind: "json"
    },
    {
      name: "filterSyncEnabled",
      kind: "json"
    },
    {
      name: "filterValue",
      kind: "json"
    },
    {
      name: "focusedColumnIndex",
      kind: "number"
    },
    {
      name: "focusedRowEnabled",
      kind: "boolean"
    },
    {
      name: "focusedRowIndex",
      kind: "number"
    },
    {
      name: "focusedRowKey",
      kind: "json"
    },
    {
      name: "hasItemsExpr",
      kind: "string"
    },
    {
      name: "headerFilter",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "highlightChanges",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemsExpr",
      kind: "string"
    },
    {
      name: "keyboardNavigation",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "string"
    },
    {
      name: "loadPanel",
      kind: "json"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "pager",
      kind: "json"
    },
    {
      name: "paging",
      kind: "json"
    },
    {
      name: "parentIdExpr",
      kind: "string"
    },
    {
      name: "remoteOperations",
      kind: "json"
    },
    {
      name: "renderAsync",
      kind: "boolean"
    },
    {
      name: "repaintChangesOnly",
      kind: "boolean"
    },
    {
      name: "rootValue",
      kind: "json"
    },
    {
      name: "rowAlternationEnabled",
      kind: "boolean"
    },
    {
      name: "rowDragging",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrolling",
      kind: "json"
    },
    {
      name: "searchPanel",
      kind: "json"
    },
    {
      name: "selectedRowKeys",
      kind: "json"
    },
    {
      name: "selection",
      kind: "json"
    },
    {
      name: "showBorders",
      kind: "boolean"
    },
    {
      name: "showColumnHeaders",
      kind: "boolean"
    },
    {
      name: "showColumnLines",
      kind: "boolean"
    },
    {
      name: "showRowLines",
      kind: "boolean"
    },
    {
      name: "sorting",
      kind: "json"
    },
    {
      name: "stateStoring",
      kind: "json"
    },
    {
      name: "syncLookupFilterValues",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "toolbar",
      kind: "json"
    },
    {
      name: "twoWayBindingEnabled",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wordWrapEnabled",
      kind: "boolean"
    }
  ],
  "dx-list": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowItemDeleting",
      kind: "boolean"
    },
    {
      name: "bounceEnabled",
      kind: "boolean"
    },
    {
      name: "collapsibleGroups",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "grouped",
      kind: "boolean"
    },
    {
      name: "groupTemplate",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "indicateLoading",
      kind: "boolean"
    },
    {
      name: "itemDeleteMode",
      kind: "select",
      'options': [
        {
          "label": "context",
          "value": "context"
        },
        {
          "label": "slideButton",
          "value": "slideButton"
        },
        {
          "label": "slideItem",
          "value": "slideItem"
        },
        {
          "label": "static",
          "value": "static"
        },
        {
          "label": "swipe",
          "value": "swipe"
        },
        {
          "label": "toggle",
          "value": "toggle"
        }
      ]
    },
    {
      name: "itemDragging",
      kind: "json"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "string"
    },
    {
      name: "menuItems",
      kind: "json"
    },
    {
      name: "menuMode",
      kind: "select",
      'options': [
        {
          "label": "context",
          "value": "context"
        },
        {
          "label": "slide",
          "value": "slide"
        }
      ]
    },
    {
      name: "nextButtonText",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "pageLoadingText",
      kind: "string"
    },
    {
      name: "pageLoadMode",
      kind: "select",
      'options': [
        {
          "label": "nextButton",
          "value": "nextButton"
        },
        {
          "label": "scrollBottom",
          "value": "scrollBottom"
        }
      ]
    },
    {
      name: "pulledDownText",
      kind: "string"
    },
    {
      name: "pullingDownText",
      kind: "string"
    },
    {
      name: "pullRefreshEnabled",
      kind: "boolean"
    },
    {
      name: "refreshingText",
      kind: "string"
    },
    {
      name: "repaintChangesOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrollByContent",
      kind: "boolean"
    },
    {
      name: "scrollByThumb",
      kind: "boolean"
    },
    {
      name: "scrollingEnabled",
      kind: "boolean"
    },
    {
      name: "searchEditorOptions",
      kind: "json"
    },
    {
      name: "searchEnabled",
      kind: "boolean"
    },
    {
      name: "searchExpr",
      kind: "json"
    },
    {
      name: "searchMode",
      kind: "select",
      'options': [
        {
          "label": "contains",
          "value": "contains"
        },
        {
          "label": "startswith",
          "value": "startswith"
        },
        {
          "label": "equals",
          "value": "equals"
        }
      ]
    },
    {
      name: "searchTimeout",
      kind: "number"
    },
    {
      name: "searchValue",
      kind: "string"
    },
    {
      name: "selectAllMode",
      kind: "select",
      'options': [
        {
          "label": "allPages",
          "value": "allPages"
        },
        {
          "label": "page",
          "value": "page"
        }
      ]
    },
    {
      name: "selectAllText",
      kind: "string"
    },
    {
      name: "selectByClick",
      kind: "boolean"
    },
    {
      name: "selectedItemKeys",
      kind: "json"
    },
    {
      name: "selectedItems",
      kind: "json"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        },
        {
          "label": "all",
          "value": "all"
        },
        {
          "label": "none",
          "value": "none"
        }
      ]
    },
    {
      name: "showScrollbar",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "never",
          "value": "never"
        },
        {
          "label": "onHover",
          "value": "onHover"
        },
        {
          "label": "onScroll",
          "value": "onScroll"
        }
      ]
    },
    {
      name: "showSelectionControls",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "useNativeScrolling",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-tree-view": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "animationEnabled",
      kind: "boolean"
    },
    {
      name: "collapseIcon",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "dataStructure",
      kind: "select",
      'options': [
        {
          "label": "plain",
          "value": "plain"
        },
        {
          "label": "tree",
          "value": "tree"
        }
      ]
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "disabledExpr",
      kind: "string"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "expandAllEnabled",
      kind: "boolean"
    },
    {
      name: "expandedExpr",
      kind: "string"
    },
    {
      name: "expandEvent",
      kind: "select",
      'options': [
        {
          "label": "dblclick",
          "value": "dblclick"
        },
        {
          "label": "click",
          "value": "click"
        }
      ]
    },
    {
      name: "expandIcon",
      kind: "string"
    },
    {
      name: "expandNodesRecursive",
      kind: "boolean"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "hasItemsExpr",
      kind: "string"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemsExpr",
      kind: "string"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "parentIdExpr",
      kind: "string"
    },
    {
      name: "rootValue",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrollDirection",
      kind: "select",
      'options': [
        {
          "label": "both",
          "value": "both"
        },
        {
          "label": "horizontal",
          "value": "horizontal"
        },
        {
          "label": "vertical",
          "value": "vertical"
        }
      ]
    },
    {
      name: "searchEditorOptions",
      kind: "json"
    },
    {
      name: "searchEnabled",
      kind: "boolean"
    },
    {
      name: "searchExpr",
      kind: "json"
    },
    {
      name: "searchMode",
      kind: "select",
      'options': [
        {
          "label": "contains",
          "value": "contains"
        },
        {
          "label": "startswith",
          "value": "startswith"
        },
        {
          "label": "equals",
          "value": "equals"
        }
      ]
    },
    {
      name: "searchTimeout",
      kind: "number"
    },
    {
      name: "searchValue",
      kind: "string"
    },
    {
      name: "selectAllText",
      kind: "string"
    },
    {
      name: "selectByClick",
      kind: "boolean"
    },
    {
      name: "selectedExpr",
      kind: "string"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "selectNodesRecursive",
      kind: "boolean"
    },
    {
      name: "showCheckBoxesMode",
      kind: "select",
      'options': [
        {
          "label": "none",
          "value": "none"
        },
        {
          "label": "normal",
          "value": "normal"
        },
        {
          "label": "selectAll",
          "value": "selectAll"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "useNativeScrolling",
      kind: "boolean"
    },
    {
      name: "virtualModeEnabled",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-pivot-grid": [
    {
      name: "allowExpandAll",
      kind: "boolean"
    },
    {
      name: "allowFiltering",
      kind: "boolean"
    },
    {
      name: "allowSorting",
      kind: "boolean"
    },
    {
      name: "allowSortingBySummary",
      kind: "boolean"
    },
    {
      name: "dataFieldArea",
      kind: "select",
      'options': [
        {
          "label": "column",
          "value": "column"
        },
        {
          "label": "row",
          "value": "row"
        }
      ]
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "encodeHtml",
      kind: "boolean"
    },
    {
      name: "export",
      kind: "json"
    },
    {
      name: "fieldChooser",
      kind: "json"
    },
    {
      name: "fieldPanel",
      kind: "json"
    },
    {
      name: "headerFilter",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hideEmptySummaryCells",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "loadPanel",
      kind: "json"
    },
    {
      name: "rowHeaderLayout",
      kind: "select",
      'options': [
        {
          "label": "standard",
          "value": "standard"
        },
        {
          "label": "tree",
          "value": "tree"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrolling",
      kind: "json"
    },
    {
      name: "showBorders",
      kind: "boolean"
    },
    {
      name: "showColumnGrandTotals",
      kind: "boolean"
    },
    {
      name: "showColumnTotals",
      kind: "boolean"
    },
    {
      name: "showRowGrandTotals",
      kind: "boolean"
    },
    {
      name: "showRowTotals",
      kind: "boolean"
    },
    {
      name: "showTotalsPrior",
      kind: "select",
      'options': [
        {
          "label": "both",
          "value": "both"
        },
        {
          "label": "columns",
          "value": "columns"
        },
        {
          "label": "none",
          "value": "none"
        },
        {
          "label": "rows",
          "value": "rows"
        }
      ]
    },
    {
      name: "stateStoring",
      kind: "json"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "texts",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wordWrapEnabled",
      kind: "boolean"
    }
  ],
  "dx-pivot-field-chooser": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowSearch",
      kind: "boolean"
    },
    {
      name: "applyChangesMode",
      kind: "select",
      'options': [
        {
          "label": "instantly",
          "value": "instantly"
        },
        {
          "label": "onDemand",
          "value": "onDemand"
        }
      ]
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "encodeHtml",
      kind: "boolean"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "headerFilter",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "layout",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "searchTimeout",
      kind: "number"
    },
    {
      name: "state",
      kind: "json"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "texts",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-filter-builder": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowHierarchicalFields",
      kind: "boolean"
    },
    {
      name: "customOperations",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "fields",
      kind: "json"
    },
    {
      name: "filterOperationDescriptions",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "groupOperationDescriptions",
      kind: "json"
    },
    {
      name: "groupOperations",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "maxGroupLevel",
      kind: "number"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-scheduler": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "adaptivityEnabled",
      kind: "boolean"
    },
    {
      name: "allDayExpr",
      kind: "string"
    },
    {
      name: "allDayPanelMode",
      kind: "select",
      'options': [
        {
          "label": "all",
          "value": "all"
        },
        {
          "label": "allDay",
          "value": "allDay"
        },
        {
          "label": "hidden",
          "value": "hidden"
        }
      ]
    },
    {
      name: "appointmentCollectorTemplate",
      kind: "json"
    },
    {
      name: "appointmentDragging",
      kind: "json"
    },
    {
      name: "appointmentTemplate",
      kind: "json"
    },
    {
      name: "appointmentTooltipTemplate",
      kind: "json"
    },
    {
      name: "cellDuration",
      kind: "number"
    },
    {
      name: "crossScrollingEnabled",
      kind: "boolean"
    },
    {
      name: "currentDate",
      kind: "string"
    },
    {
      name: "currentView",
      kind: "string"
    },
    {
      name: "dataCellTemplate",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "dateCellTemplate",
      kind: "json"
    },
    {
      name: "dateSerializationFormat",
      kind: "string"
    },
    {
      name: "descriptionExpr",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "dropDownAppointmentTemplate",
      kind: "json"
    },
    {
      name: "editing",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "endDateExpr",
      kind: "string"
    },
    {
      name: "endDateTimeZoneExpr",
      kind: "string"
    },
    {
      name: "endDayHour",
      kind: "number"
    },
    {
      name: "firstDayOfWeek",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "groupByDate",
      kind: "boolean"
    },
    {
      name: "groups",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "indicatorUpdateInterval",
      kind: "number"
    },
    {
      name: "max",
      kind: "string"
    },
    {
      name: "maxAppointmentsPerCell",
      kind: "json"
    },
    {
      name: "min",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "offset",
      kind: "number"
    },
    {
      name: "recurrenceEditMode",
      kind: "select",
      'options': [
        {
          "label": "dialog",
          "value": "dialog"
        },
        {
          "label": "occurrence",
          "value": "occurrence"
        },
        {
          "label": "series",
          "value": "series"
        }
      ]
    },
    {
      name: "recurrenceExceptionExpr",
      kind: "string"
    },
    {
      name: "recurrenceRuleExpr",
      kind: "string"
    },
    {
      name: "remoteFiltering",
      kind: "boolean"
    },
    {
      name: "resourceCellTemplate",
      kind: "json"
    },
    {
      name: "resources",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrolling",
      kind: "json"
    },
    {
      name: "selectedCellData",
      kind: "json"
    },
    {
      name: "shadeUntilCurrentTime",
      kind: "boolean"
    },
    {
      name: "showAllDayPanel",
      kind: "boolean"
    },
    {
      name: "showCurrentTimeIndicator",
      kind: "boolean"
    },
    {
      name: "startDateExpr",
      kind: "string"
    },
    {
      name: "startDateTimeZoneExpr",
      kind: "string"
    },
    {
      name: "startDayHour",
      kind: "number"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "textExpr",
      kind: "string"
    },
    {
      name: "timeCellTemplate",
      kind: "json"
    },
    {
      name: "timeZone",
      kind: "string"
    },
    {
      name: "useDropDownViewSwitcher",
      kind: "boolean"
    },
    {
      name: "views",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-gantt": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowSelection",
      kind: "boolean"
    },
    {
      name: "columns",
      kind: "json"
    },
    {
      name: "contextMenu",
      kind: "json"
    },
    {
      name: "dependencies",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "editing",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "endDateRange",
      kind: "string"
    },
    {
      name: "filterRow",
      kind: "json"
    },
    {
      name: "firstDayOfWeek",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "headerFilter",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "resourceAssignments",
      kind: "json"
    },
    {
      name: "resources",
      kind: "json"
    },
    {
      name: "rootValue",
      kind: "json"
    },
    {
      name: "scaleType",
      kind: "select",
      'options': [
        {
          "label": "auto",
          "value": "auto"
        },
        {
          "label": "minutes",
          "value": "minutes"
        },
        {
          "label": "hours",
          "value": "hours"
        },
        {
          "label": "sixHours",
          "value": "sixHours"
        },
        {
          "label": "days",
          "value": "days"
        },
        {
          "label": "weeks",
          "value": "weeks"
        },
        {
          "label": "months",
          "value": "months"
        },
        {
          "label": "quarters",
          "value": "quarters"
        },
        {
          "label": "years",
          "value": "years"
        }
      ]
    },
    {
      name: "scaleTypeRange",
      kind: "json"
    },
    {
      name: "selectedRowKey",
      kind: "json"
    },
    {
      name: "showDependencies",
      kind: "boolean"
    },
    {
      name: "showResources",
      kind: "boolean"
    },
    {
      name: "showRowLines",
      kind: "boolean"
    },
    {
      name: "sorting",
      kind: "json"
    },
    {
      name: "startDateRange",
      kind: "string"
    },
    {
      name: "stripLines",
      kind: "json"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "taskContentTemplate",
      kind: "json"
    },
    {
      name: "taskListWidth",
      kind: "number"
    },
    {
      name: "taskProgressTooltipContentTemplate",
      kind: "json"
    },
    {
      name: "tasks",
      kind: "json"
    },
    {
      name: "taskTimeTooltipContentTemplate",
      kind: "json"
    },
    {
      name: "taskTitlePosition",
      kind: "select",
      'options': [
        {
          "label": "inside",
          "value": "inside"
        },
        {
          "label": "outside",
          "value": "outside"
        },
        {
          "label": "none",
          "value": "none"
        }
      ]
    },
    {
      name: "taskTooltipContentTemplate",
      kind: "json"
    },
    {
      name: "toolbar",
      kind: "json"
    },
    {
      name: "validation",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-file-manager": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowedFileExtensions",
      kind: "json"
    },
    {
      name: "contextMenu",
      kind: "json"
    },
    {
      name: "currentPath",
      kind: "string"
    },
    {
      name: "currentPathKeys",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "fileSystemProvider",
      kind: "json"
    },
    {
      name: "focusedItemKey",
      kind: "string"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemView",
      kind: "json"
    },
    {
      name: "notifications",
      kind: "json"
    },
    {
      name: "permissions",
      kind: "json"
    },
    {
      name: "rootFolderName",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectedItemKeys",
      kind: "json"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "toolbar",
      kind: "json"
    },
    {
      name: "upload",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-text-box": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "mask",
      kind: "string"
    },
    {
      name: "maskChar",
      kind: "string"
    },
    {
      name: "maskInvalidMessage",
      kind: "string"
    },
    {
      name: "maskRules",
      kind: "json"
    },
    {
      name: "maxLength",
      kind: "string"
    },
    {
      name: "mode",
      kind: "select",
      'options': [
        {
          "label": "email",
          "value": "email"
        },
        {
          "label": "password",
          "value": "password"
        },
        {
          "label": "search",
          "value": "search"
        },
        {
          "label": "tel",
          "value": "tel"
        },
        {
          "label": "text",
          "value": "text"
        },
        {
          "label": "url",
          "value": "url"
        }
      ]
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showMaskMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "onFocus",
          "value": "onFocus"
        }
      ]
    },
    {
      name: "spellcheck",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "useMaskedValue",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "string"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-text-area": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "autoResizeEnabled",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "maxHeight",
      kind: "string"
    },
    {
      name: "maxLength",
      kind: "string"
    },
    {
      name: "minHeight",
      kind: "string"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "spellcheck",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "string"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-number-box": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "format",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "invalidValueMessage",
      kind: "string"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "max",
      kind: "number"
    },
    {
      name: "min",
      kind: "number"
    },
    {
      name: "mode",
      kind: "select",
      'options': [
        {
          "label": "number",
          "value": "number"
        },
        {
          "label": "text",
          "value": "text"
        },
        {
          "label": "tel",
          "value": "tel"
        }
      ]
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showSpinButtons",
      kind: "boolean"
    },
    {
      name: "step",
      kind: "number"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "useLargeSpinButtons",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "number"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-check-box": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "enableThreeStateBehavior",
      kind: "boolean"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "iconSize",
      kind: "string"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-switch": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "switchedOffText",
      kind: "string"
    },
    {
      name: "switchedOnText",
      kind: "string"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-slider": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "keyStep",
      kind: "number"
    },
    {
      name: "label",
      kind: "json"
    },
    {
      name: "max",
      kind: "number"
    },
    {
      name: "min",
      kind: "number"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showRange",
      kind: "boolean"
    },
    {
      name: "step",
      kind: "number"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "number"
    },
    {
      name: "valueChangeMode",
      kind: "select",
      'options': [
        {
          "label": "onHandleMove",
          "value": "onHandleMove"
        },
        {
          "label": "onHandleRelease",
          "value": "onHandleRelease"
        }
      ]
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-range-slider": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "end",
      kind: "number"
    },
    {
      name: "endName",
      kind: "string"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "keyStep",
      kind: "number"
    },
    {
      name: "label",
      kind: "json"
    },
    {
      name: "max",
      kind: "number"
    },
    {
      name: "min",
      kind: "number"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showRange",
      kind: "boolean"
    },
    {
      name: "start",
      kind: "number"
    },
    {
      name: "startName",
      kind: "string"
    },
    {
      name: "step",
      kind: "number"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "tooltip",
      kind: "json"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "valueChangeMode",
      kind: "select",
      'options': [
        {
          "label": "onHandleMove",
          "value": "onHandleMove"
        },
        {
          "label": "onHandleRelease",
          "value": "onHandleRelease"
        }
      ]
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-calendar": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "cellTemplate",
      kind: "json"
    },
    {
      name: "dateSerializationFormat",
      kind: "string"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "disabledDates",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "firstDayOfWeek",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "max",
      kind: "string"
    },
    {
      name: "maxZoomLevel",
      kind: "select",
      'options': [
        {
          "label": "century",
          "value": "century"
        },
        {
          "label": "decade",
          "value": "decade"
        },
        {
          "label": "month",
          "value": "month"
        },
        {
          "label": "year",
          "value": "year"
        }
      ]
    },
    {
      name: "min",
      kind: "string"
    },
    {
      name: "minZoomLevel",
      kind: "select",
      'options': [
        {
          "label": "century",
          "value": "century"
        },
        {
          "label": "decade",
          "value": "decade"
        },
        {
          "label": "month",
          "value": "month"
        },
        {
          "label": "year",
          "value": "year"
        }
      ]
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        },
        {
          "label": "range",
          "value": "range"
        }
      ]
    },
    {
      name: "selectWeekOnClick",
      kind: "boolean"
    },
    {
      name: "showTodayButton",
      kind: "boolean"
    },
    {
      name: "showWeekNumbers",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "weekNumberRule",
      kind: "select",
      'options': [
        {
          "label": "auto",
          "value": "auto"
        },
        {
          "label": "firstDay",
          "value": "firstDay"
        },
        {
          "label": "fullWeek",
          "value": "fullWeek"
        },
        {
          "label": "firstFourDays",
          "value": "firstFourDays"
        }
      ]
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "zoomLevel",
      kind: "select",
      'options': [
        {
          "label": "century",
          "value": "century"
        },
        {
          "label": "decade",
          "value": "decade"
        },
        {
          "label": "month",
          "value": "month"
        },
        {
          "label": "year",
          "value": "year"
        }
      ]
    }
  ],
  "dx-date-box": [
    {
      name: "acceptCustomValue",
      kind: "boolean"
    },
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "adaptivityEnabled",
      kind: "boolean"
    },
    {
      name: "applyButtonText",
      kind: "string"
    },
    {
      name: "applyValueMode",
      kind: "select",
      'options': [
        {
          "label": "instantly",
          "value": "instantly"
        },
        {
          "label": "useButtons",
          "value": "useButtons"
        }
      ]
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "calendarOptions",
      kind: "json"
    },
    {
      name: "cancelButtonText",
      kind: "string"
    },
    {
      name: "dateOutOfRangeMessage",
      kind: "string"
    },
    {
      name: "dateSerializationFormat",
      kind: "string"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "disabledDates",
      kind: "json"
    },
    {
      name: "displayFormat",
      kind: "json"
    },
    {
      name: "dropDownButtonTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "interval",
      kind: "number"
    },
    {
      name: "invalidDateMessage",
      kind: "string"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "max",
      kind: "string"
    },
    {
      name: "maxLength",
      kind: "string"
    },
    {
      name: "min",
      kind: "string"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "openOnFieldClick",
      kind: "boolean"
    },
    {
      name: "pickerType",
      kind: "select",
      'options': [
        {
          "label": "calendar",
          "value": "calendar"
        },
        {
          "label": "list",
          "value": "list"
        },
        {
          "label": "native",
          "value": "native"
        },
        {
          "label": "rollers",
          "value": "rollers"
        }
      ]
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showAnalogClock",
      kind: "boolean"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDropDownButton",
      kind: "boolean"
    },
    {
      name: "spellcheck",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "todayButtonText",
      kind: "string"
    },
    {
      name: "type",
      kind: "select",
      'options': [
        {
          "label": "date",
          "value": "date"
        },
        {
          "label": "datetime",
          "value": "datetime"
        },
        {
          "label": "time",
          "value": "time"
        }
      ]
    },
    {
      name: "useMaskBehavior",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "string"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-date-range-box": [
    {
      name: "acceptCustomValue",
      kind: "boolean"
    },
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "applyButtonText",
      kind: "string"
    },
    {
      name: "applyValueMode",
      kind: "select",
      'options': [
        {
          "label": "instantly",
          "value": "instantly"
        },
        {
          "label": "useButtons",
          "value": "useButtons"
        }
      ]
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "calendarOptions",
      kind: "json"
    },
    {
      name: "cancelButtonText",
      kind: "string"
    },
    {
      name: "dateSerializationFormat",
      kind: "string"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "disableOutOfRangeSelection",
      kind: "boolean"
    },
    {
      name: "displayFormat",
      kind: "json"
    },
    {
      name: "dropDownButtonTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "endDate",
      kind: "string"
    },
    {
      name: "endDateInputAttr",
      kind: "json"
    },
    {
      name: "endDateLabel",
      kind: "string"
    },
    {
      name: "endDateName",
      kind: "string"
    },
    {
      name: "endDateOutOfRangeMessage",
      kind: "string"
    },
    {
      name: "endDatePlaceholder",
      kind: "string"
    },
    {
      name: "endDateText",
      kind: "string"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "invalidEndDateMessage",
      kind: "string"
    },
    {
      name: "invalidStartDateMessage",
      kind: "string"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "max",
      kind: "string"
    },
    {
      name: "min",
      kind: "string"
    },
    {
      name: "multiView",
      kind: "boolean"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "openOnFieldClick",
      kind: "boolean"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDropDownButton",
      kind: "boolean"
    },
    {
      name: "spellcheck",
      kind: "boolean"
    },
    {
      name: "startDate",
      kind: "string"
    },
    {
      name: "startDateInputAttr",
      kind: "json"
    },
    {
      name: "startDateLabel",
      kind: "string"
    },
    {
      name: "startDateName",
      kind: "string"
    },
    {
      name: "startDateOutOfRangeMessage",
      kind: "string"
    },
    {
      name: "startDatePlaceholder",
      kind: "string"
    },
    {
      name: "startDateText",
      kind: "string"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "todayButtonText",
      kind: "string"
    },
    {
      name: "useMaskBehavior",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-color-box": [
    {
      name: "acceptCustomValue",
      kind: "boolean"
    },
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "applyButtonText",
      kind: "string"
    },
    {
      name: "applyValueMode",
      kind: "select",
      'options': [
        {
          "label": "instantly",
          "value": "instantly"
        },
        {
          "label": "useButtons",
          "value": "useButtons"
        }
      ]
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "cancelButtonText",
      kind: "string"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "dropDownButtonTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "editAlphaChannel",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "fieldTemplate",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "keyStep",
      kind: "number"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "openOnFieldClick",
      kind: "boolean"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDropDownButton",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-select-box": [
    {
      name: "acceptCustomValue",
      kind: "boolean"
    },
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "customItemCreateEvent",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "displayValue",
      kind: "string"
    },
    {
      name: "dropDownButtonTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "fieldTemplate",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "grouped",
      kind: "boolean"
    },
    {
      name: "groupTemplate",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "maxLength",
      kind: "string"
    },
    {
      name: "minSearchLength",
      kind: "number"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "openOnFieldClick",
      kind: "boolean"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "searchEnabled",
      kind: "boolean"
    },
    {
      name: "searchExpr",
      kind: "json"
    },
    {
      name: "searchMode",
      kind: "select",
      'options': [
        {
          "label": "contains",
          "value": "contains"
        },
        {
          "label": "startswith",
          "value": "startswith"
        }
      ]
    },
    {
      name: "searchTimeout",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDataBeforeSearch",
      kind: "boolean"
    },
    {
      name: "showDropDownButton",
      kind: "boolean"
    },
    {
      name: "showSelectionControls",
      kind: "boolean"
    },
    {
      name: "spellcheck",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "useItemTextAsTitle",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "valueExpr",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapItemText",
      kind: "boolean"
    }
  ],
  "dx-lookup": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "applyButtonText",
      kind: "string"
    },
    {
      name: "applyValueMode",
      kind: "select",
      'options': [
        {
          "label": "instantly",
          "value": "instantly"
        },
        {
          "label": "useButtons",
          "value": "useButtons"
        }
      ]
    },
    {
      name: "cancelButtonText",
      kind: "string"
    },
    {
      name: "cleanSearchOnOpening",
      kind: "boolean"
    },
    {
      name: "clearButtonText",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "displayValue",
      kind: "string"
    },
    {
      name: "dropDownCentered",
      kind: "boolean"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "fieldTemplate",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "fullScreen",
      kind: "boolean"
    },
    {
      name: "grouped",
      kind: "boolean"
    },
    {
      name: "groupTemplate",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "minSearchLength",
      kind: "number"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "nextButtonText",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "pageLoadingText",
      kind: "string"
    },
    {
      name: "pageLoadMode",
      kind: "select",
      'options': [
        {
          "label": "nextButton",
          "value": "nextButton"
        },
        {
          "label": "scrollBottom",
          "value": "scrollBottom"
        }
      ]
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "pulledDownText",
      kind: "string"
    },
    {
      name: "pullingDownText",
      kind: "string"
    },
    {
      name: "pullRefreshEnabled",
      kind: "boolean"
    },
    {
      name: "refreshingText",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "searchEnabled",
      kind: "boolean"
    },
    {
      name: "searchExpr",
      kind: "json"
    },
    {
      name: "searchMode",
      kind: "select",
      'options': [
        {
          "label": "contains",
          "value": "contains"
        },
        {
          "label": "startswith",
          "value": "startswith"
        }
      ]
    },
    {
      name: "searchPlaceholder",
      kind: "string"
    },
    {
      name: "searchStartEvent",
      kind: "string"
    },
    {
      name: "searchTimeout",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "showCancelButton",
      kind: "boolean"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDataBeforeSearch",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "useItemTextAsTitle",
      kind: "boolean"
    },
    {
      name: "useNativeScrolling",
      kind: "boolean"
    },
    {
      name: "usePopover",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "valueExpr",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapItemText",
      kind: "boolean"
    }
  ],
  "dx-tag-box": [
    {
      name: "acceptCustomValue",
      kind: "boolean"
    },
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "applyValueMode",
      kind: "select",
      'options': [
        {
          "label": "instantly",
          "value": "instantly"
        },
        {
          "label": "useButtons",
          "value": "useButtons"
        }
      ]
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "customItemCreateEvent",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "dropDownButtonTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "fieldTemplate",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "grouped",
      kind: "boolean"
    },
    {
      name: "groupTemplate",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hideSelectedItems",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "maxDisplayedTags",
      kind: "number"
    },
    {
      name: "maxFilterQueryLength",
      kind: "number"
    },
    {
      name: "maxLength",
      kind: "string"
    },
    {
      name: "minSearchLength",
      kind: "number"
    },
    {
      name: "multiline",
      kind: "boolean"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "openOnFieldClick",
      kind: "boolean"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "searchEnabled",
      kind: "boolean"
    },
    {
      name: "searchExpr",
      kind: "json"
    },
    {
      name: "searchMode",
      kind: "select",
      'options': [
        {
          "label": "contains",
          "value": "contains"
        },
        {
          "label": "startswith",
          "value": "startswith"
        }
      ]
    },
    {
      name: "searchTimeout",
      kind: "number"
    },
    {
      name: "selectAllMode",
      kind: "select",
      'options': [
        {
          "label": "allPages",
          "value": "allPages"
        },
        {
          "label": "page",
          "value": "page"
        }
      ]
    },
    {
      name: "selectAllText",
      kind: "string"
    },
    {
      name: "selectedItems",
      kind: "json"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDataBeforeSearch",
      kind: "boolean"
    },
    {
      name: "showDropDownButton",
      kind: "boolean"
    },
    {
      name: "showMultiTagOnly",
      kind: "boolean"
    },
    {
      name: "showSelectionControls",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "tagTemplate",
      kind: "json"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "useItemTextAsTitle",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "valueExpr",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapItemText",
      kind: "boolean"
    }
  ],
  "dx-autocomplete": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayValue",
      kind: "string"
    },
    {
      name: "dropDownButtonTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "grouped",
      kind: "boolean"
    },
    {
      name: "groupTemplate",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "maxItemCount",
      kind: "number"
    },
    {
      name: "maxLength",
      kind: "string"
    },
    {
      name: "minSearchLength",
      kind: "number"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "openOnFieldClick",
      kind: "boolean"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "searchExpr",
      kind: "json"
    },
    {
      name: "searchMode",
      kind: "select",
      'options': [
        {
          "label": "contains",
          "value": "contains"
        },
        {
          "label": "startswith",
          "value": "startswith"
        }
      ]
    },
    {
      name: "searchTimeout",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDropDownButton",
      kind: "boolean"
    },
    {
      name: "spellcheck",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "useItemTextAsTitle",
      kind: "boolean"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "string"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "valueExpr",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapItemText",
      kind: "boolean"
    }
  ],
  "dx-radio-group": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "layout",
      kind: "select",
      'options': [
        {
          "label": "horizontal",
          "value": "horizontal"
        },
        {
          "label": "vertical",
          "value": "vertical"
        }
      ]
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "valueExpr",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-drop-down-button": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "dropDownContentTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "icon",
      kind: "string"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "selectedItemKey",
      kind: "string"
    },
    {
      name: "showArrowIcon",
      kind: "boolean"
    },
    {
      name: "splitButton",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "text",
          "value": "text"
        },
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "contained",
          "value": "contained"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "type",
      kind: "string"
    },
    {
      name: "useItemTextAsTitle",
      kind: "boolean"
    },
    {
      name: "useSelectMode",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapItemText",
      kind: "boolean"
    }
  ],
  "dx-drop-down-box": [
    {
      name: "acceptCustomValue",
      kind: "boolean"
    },
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "buttons",
      kind: "json"
    },
    {
      name: "contentTemplate",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "dropDownButtonTemplate",
      kind: "json"
    },
    {
      name: "dropDownOptions",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "fieldTemplate",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "maxLength",
      kind: "string"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "opened",
      kind: "boolean"
    },
    {
      name: "openOnFieldClick",
      kind: "boolean"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showClearButton",
      kind: "boolean"
    },
    {
      name: "showDropDownButton",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "valueChangeEvent",
      kind: "string"
    },
    {
      name: "valueExpr",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-file-uploader": [
    {
      name: "accept",
      kind: "string"
    },
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowCanceling",
      kind: "boolean"
    },
    {
      name: "allowedFileExtensions",
      kind: "json"
    },
    {
      name: "chunkSize",
      kind: "number"
    },
    {
      name: "dialogTrigger",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "dropZone",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "inputAttr",
      kind: "json"
    },
    {
      name: "invalidFileExtensionMessage",
      kind: "string"
    },
    {
      name: "invalidMaxFileSizeMessage",
      kind: "string"
    },
    {
      name: "invalidMinFileSizeMessage",
      kind: "string"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "labelText",
      kind: "string"
    },
    {
      name: "maxFileSize",
      kind: "number"
    },
    {
      name: "minFileSize",
      kind: "number"
    },
    {
      name: "multiple",
      kind: "boolean"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "progress",
      kind: "number"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "readyToUploadMessage",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectButtonText",
      kind: "string"
    },
    {
      name: "showFileList",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "uploadAbortedMessage",
      kind: "string"
    },
    {
      name: "uploadButtonText",
      kind: "string"
    },
    {
      name: "uploadCustomData",
      kind: "json"
    },
    {
      name: "uploadedMessage",
      kind: "string"
    },
    {
      name: "uploadFailedMessage",
      kind: "string"
    },
    {
      name: "uploadHeaders",
      kind: "json"
    },
    {
      name: "uploadMethod",
      kind: "select",
      'options': [
        {
          "label": "POST",
          "value": "POST"
        },
        {
          "label": "PUT",
          "value": "PUT"
        }
      ]
    },
    {
      name: "uploadMode",
      kind: "select",
      'options': [
        {
          "label": "instantly",
          "value": "instantly"
        },
        {
          "label": "useButtons",
          "value": "useButtons"
        },
        {
          "label": "useForm",
          "value": "useForm"
        }
      ]
    },
    {
      name: "uploadUrl",
      kind: "string"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-html-editor": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowSoftLineBreak",
      kind: "boolean"
    },
    {
      name: "converter",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "imageUpload",
      kind: "json"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "mediaResizing",
      kind: "json"
    },
    {
      name: "mentions",
      kind: "json"
    },
    {
      name: "name",
      kind: "string"
    },
    {
      name: "placeholder",
      kind: "string"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "underlined",
          "value": "underlined"
        },
        {
          "label": "filled",
          "value": "filled"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "tableContextMenu",
      kind: "json"
    },
    {
      name: "tableResizing",
      kind: "json"
    },
    {
      name: "toolbar",
      kind: "json"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "variables",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-recurrence-editor": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-tabs": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "iconPosition",
      kind: "select",
      'options': [
        {
          "label": "top",
          "value": "top"
        },
        {
          "label": "end",
          "value": "end"
        },
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "start",
          "value": "start"
        }
      ]
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "string"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "orientation",
      kind: "select",
      'options': [
        {
          "label": "horizontal",
          "value": "horizontal"
        },
        {
          "label": "vertical",
          "value": "vertical"
        }
      ]
    },
    {
      name: "repaintChangesOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrollByContent",
      kind: "boolean"
    },
    {
      name: "scrollingEnabled",
      kind: "boolean"
    },
    {
      name: "selectedIndex",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "selectedItemKeys",
      kind: "json"
    },
    {
      name: "selectedItems",
      kind: "json"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        }
      ]
    },
    {
      name: "showNavButtons",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "primary",
          "value": "primary"
        },
        {
          "label": "secondary",
          "value": "secondary"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-tab-panel": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "animationEnabled",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "iconPosition",
      kind: "select",
      'options': [
        {
          "label": "top",
          "value": "top"
        },
        {
          "label": "end",
          "value": "end"
        },
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "start",
          "value": "start"
        }
      ]
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "itemTitleTemplate",
      kind: "json"
    },
    {
      name: "loop",
      kind: "boolean"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "repaintChangesOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "scrollByContent",
      kind: "boolean"
    },
    {
      name: "scrollingEnabled",
      kind: "boolean"
    },
    {
      name: "selectedIndex",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "showNavButtons",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "primary",
          "value": "primary"
        },
        {
          "label": "secondary",
          "value": "secondary"
        }
      ]
    },
    {
      name: "swipeEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "tabsPosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-accordion": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "animationDuration",
      kind: "number"
    },
    {
      name: "collapsible",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "itemTitleTemplate",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "string"
    },
    {
      name: "multiple",
      kind: "boolean"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "repaintChangesOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectedIndex",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "selectedItemKeys",
      kind: "json"
    },
    {
      name: "selectedItems",
      kind: "json"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-menu": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "adaptivityEnabled",
      kind: "boolean"
    },
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "cssClass",
      kind: "string"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "disabledExpr",
      kind: "string"
    },
    {
      name: "displayExpr",
      kind: "json"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hideSubmenuOnMouseLeave",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemsExpr",
      kind: "string"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "orientation",
      kind: "select",
      'options': [
        {
          "label": "horizontal",
          "value": "horizontal"
        },
        {
          "label": "vertical",
          "value": "vertical"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectByClick",
      kind: "boolean"
    },
    {
      name: "selectedExpr",
      kind: "string"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "none",
          "value": "none"
        }
      ]
    },
    {
      name: "showFirstSubmenuMode",
      kind: "json"
    },
    {
      name: "showSubmenuMode",
      kind: "json"
    },
    {
      name: "submenuDirection",
      kind: "select",
      'options': [
        {
          "label": "auto",
          "value": "auto"
        },
        {
          "label": "leftOrTop",
          "value": "leftOrTop"
        },
        {
          "label": "rightOrBottom",
          "value": "rightOrBottom"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-toolbar": [
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "menuItemTemplate",
      kind: "json"
    },
    {
      name: "multiline",
      kind: "boolean"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-tile-view": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "baseItemHeight",
      kind: "number"
    },
    {
      name: "baseItemWidth",
      kind: "number"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "direction",
      kind: "select",
      'options': [
        {
          "label": "horizontal",
          "value": "horizontal"
        },
        {
          "label": "vertical",
          "value": "vertical"
        }
      ]
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "itemMargin",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showScrollbar",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "never",
          "value": "never"
        },
        {
          "label": "onHover",
          "value": "onHover"
        },
        {
          "label": "onScroll",
          "value": "onScroll"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-multi-view": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "animationEnabled",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "loop",
      kind: "boolean"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectedIndex",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "swipeEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-splitter": [
    {
      name: "allowKeyboardNavigation",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "orientation",
      kind: "select",
      'options': [
        {
          "label": "horizontal",
          "value": "horizontal"
        },
        {
          "label": "vertical",
          "value": "vertical"
        }
      ]
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "separatorSize",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-gallery": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "animationDuration",
      kind: "number"
    },
    {
      name: "animationEnabled",
      kind: "boolean"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "indicatorEnabled",
      kind: "boolean"
    },
    {
      name: "initialItemWidth",
      kind: "number"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "loop",
      kind: "boolean"
    },
    {
      name: "noDataText",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectedIndex",
      kind: "number"
    },
    {
      name: "selectedItem",
      kind: "json"
    },
    {
      name: "showIndicator",
      kind: "boolean"
    },
    {
      name: "showNavButtons",
      kind: "boolean"
    },
    {
      name: "slideshowDelay",
      kind: "number"
    },
    {
      name: "stretchImages",
      kind: "boolean"
    },
    {
      name: "swipeEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapAround",
      kind: "boolean"
    }
  ],
  "dx-pagination": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "allowedPageSizes",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "displayMode",
      kind: "select",
      'options': [
        {
          "label": "adaptive",
          "value": "adaptive"
        },
        {
          "label": "compact",
          "value": "compact"
        },
        {
          "label": "full",
          "value": "full"
        }
      ]
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "infoText",
      kind: "string"
    },
    {
      name: "itemCount",
      kind: "number"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "pageIndex",
      kind: "number"
    },
    {
      name: "pageSize",
      kind: "number"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showInfo",
      kind: "boolean"
    },
    {
      name: "showNavigationButtons",
      kind: "boolean"
    },
    {
      name: "showPageSizeSelector",
      kind: "json"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-speed-dial-action": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "icon",
      kind: "string"
    },
    {
      name: "index",
      kind: "number"
    },
    {
      name: "label",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    }
  ],
  "dx-chat": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "alerts",
      kind: "json"
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "dayHeaderFormat",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "messageTemplate",
      kind: "json"
    },
    {
      name: "messageTimestampFormat",
      kind: "json"
    },
    {
      name: "reloadOnChange",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showAvatar",
      kind: "boolean"
    },
    {
      name: "showDayHeaders",
      kind: "boolean"
    },
    {
      name: "showMessageTimestamp",
      kind: "boolean"
    },
    {
      name: "showUserName",
      kind: "boolean"
    },
    {
      name: "typingUsers",
      kind: "json"
    },
    {
      name: "user",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-box": [
    {
      name: "align",
      kind: "select",
      'options': [
        {
          "label": "center",
          "value": "center"
        },
        {
          "label": "end",
          "value": "end"
        },
        {
          "label": "space-around",
          "value": "space-around"
        },
        {
          "label": "space-between",
          "value": "space-between"
        },
        {
          "label": "start",
          "value": "start"
        }
      ]
    },
    {
      name: "crossAlign",
      kind: "select",
      'options': [
        {
          "label": "center",
          "value": "center"
        },
        {
          "label": "end",
          "value": "end"
        },
        {
          "label": "start",
          "value": "start"
        },
        {
          "label": "stretch",
          "value": "stretch"
        }
      ]
    },
    {
      name: "dataSource",
      kind: "json"
    },
    {
      name: "direction",
      kind: "select",
      'options': [
        {
          "label": "col",
          "value": "col"
        },
        {
          "label": "row",
          "value": "row"
        }
      ]
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "itemHoldTimeout",
      kind: "number"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "itemTemplate",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-button-group": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "buttonTemplate",
      kind: "json"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "items",
      kind: "json"
    },
    {
      name: "keyExpr",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "selectedItemKeys",
      kind: "json"
    },
    {
      name: "selectedItems",
      kind: "json"
    },
    {
      name: "selectionMode",
      kind: "select",
      'options': [
        {
          "label": "single",
          "value": "single"
        },
        {
          "label": "multiple",
          "value": "multiple"
        },
        {
          "label": "none",
          "value": "none"
        }
      ]
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "text",
          "value": "text"
        },
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "contained",
          "value": "contained"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-button": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "activeStateEnabled",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "icon",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "stylingMode",
      kind: "select",
      'options': [
        {
          "label": "text",
          "value": "text"
        },
        {
          "label": "outlined",
          "value": "outlined"
        },
        {
          "label": "contained",
          "value": "contained"
        }
      ]
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "text",
      kind: "string"
    },
    {
      name: "type",
      kind: "string"
    },
    {
      name: "useSubmitBehavior",
      kind: "boolean"
    },
    {
      name: "validationGroup",
      kind: "string"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-progress-bar": [
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "isDirty",
      kind: "boolean"
    },
    {
      name: "isValid",
      kind: "boolean"
    },
    {
      name: "max",
      kind: "number"
    },
    {
      name: "min",
      kind: "number"
    },
    {
      name: "readOnly",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "showStatus",
      kind: "boolean"
    },
    {
      name: "statusFormat",
      kind: "json"
    },
    {
      name: "validationError",
      kind: "json"
    },
    {
      name: "validationErrors",
      kind: "json"
    },
    {
      name: "validationMessageMode",
      kind: "select",
      'options': [
        {
          "label": "always",
          "value": "always"
        },
        {
          "label": "auto",
          "value": "auto"
        }
      ]
    },
    {
      name: "validationMessagePosition",
      kind: "select",
      'options': [
        {
          "label": "bottom",
          "value": "bottom"
        },
        {
          "label": "left",
          "value": "left"
        },
        {
          "label": "right",
          "value": "right"
        },
        {
          "label": "top",
          "value": "top"
        }
      ]
    },
    {
      name: "validationStatus",
      kind: "select",
      'options': [
        {
          "label": "valid",
          "value": "valid"
        },
        {
          "label": "invalid",
          "value": "invalid"
        },
        {
          "label": "pending",
          "value": "pending"
        }
      ]
    },
    {
      name: "value",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-load-indicator": [
    {
      name: "elementAttr",
      kind: "json"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "indicatorSrc",
      kind: "string"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    }
  ],
  "dx-popup": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "closeOnOutsideClick",
      kind: "json"
    },
    {
      name: "container",
      kind: "json"
    },
    {
      name: "contentTemplate",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "disabled",
      kind: "boolean"
    },
    {
      name: "dragAndResizeArea",
      kind: "json"
    },
    {
      name: "dragEnabled",
      kind: "boolean"
    },
    {
      name: "dragOutsideBoundary",
      kind: "boolean"
    },
    {
      name: "enableBodyScroll",
      kind: "boolean"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "fullScreen",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hideOnOutsideClick",
      kind: "json"
    },
    {
      name: "hideOnParentScroll",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "maxHeight",
      kind: "string"
    },
    {
      name: "maxWidth",
      kind: "string"
    },
    {
      name: "minHeight",
      kind: "string"
    },
    {
      name: "minWidth",
      kind: "string"
    },
    {
      name: "position",
      kind: "json"
    },
    {
      name: "resizeEnabled",
      kind: "boolean"
    },
    {
      name: "restorePosition",
      kind: "boolean"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "shading",
      kind: "boolean"
    },
    {
      name: "shadingColor",
      kind: "string"
    },
    {
      name: "showCloseButton",
      kind: "boolean"
    },
    {
      name: "showTitle",
      kind: "boolean"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "title",
      kind: "string"
    },
    {
      name: "titleTemplate",
      kind: "json"
    },
    {
      name: "toolbarItems",
      kind: "json"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapperAttr",
      kind: "json"
    }
  ],
  "dx-toast": [
    {
      name: "accessKey",
      kind: "string"
    },
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "closeOnClick",
      kind: "boolean"
    },
    {
      name: "closeOnOutsideClick",
      kind: "json"
    },
    {
      name: "closeOnSwipe",
      kind: "boolean"
    },
    {
      name: "contentTemplate",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "displayTime",
      kind: "number"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hideOnOutsideClick",
      kind: "json"
    },
    {
      name: "hideOnParentScroll",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "maxHeight",
      kind: "string"
    },
    {
      name: "maxWidth",
      kind: "string"
    },
    {
      name: "message",
      kind: "string"
    },
    {
      name: "minHeight",
      kind: "string"
    },
    {
      name: "minWidth",
      kind: "string"
    },
    {
      name: "position",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "shading",
      kind: "boolean"
    },
    {
      name: "shadingColor",
      kind: "string"
    },
    {
      name: "tabIndex",
      kind: "number"
    },
    {
      name: "type",
      kind: "select",
      'options': [
        {
          "label": "custom",
          "value": "custom"
        },
        {
          "label": "error",
          "value": "error"
        },
        {
          "label": "info",
          "value": "info"
        },
        {
          "label": "success",
          "value": "success"
        },
        {
          "label": "warning",
          "value": "warning"
        }
      ]
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapperAttr",
      kind: "json"
    }
  ],
  "dx-load-panel": [
    {
      name: "animation",
      kind: "json"
    },
    {
      name: "closeOnOutsideClick",
      kind: "json"
    },
    {
      name: "container",
      kind: "json"
    },
    {
      name: "deferRendering",
      kind: "boolean"
    },
    {
      name: "delay",
      kind: "number"
    },
    {
      name: "focusStateEnabled",
      kind: "boolean"
    },
    {
      name: "height",
      kind: "string"
    },
    {
      name: "hideOnOutsideClick",
      kind: "json"
    },
    {
      name: "hideOnParentScroll",
      kind: "boolean"
    },
    {
      name: "hint",
      kind: "string"
    },
    {
      name: "hoverStateEnabled",
      kind: "boolean"
    },
    {
      name: "indicatorSrc",
      kind: "string"
    },
    {
      name: "maxHeight",
      kind: "string"
    },
    {
      name: "maxWidth",
      kind: "string"
    },
    {
      name: "message",
      kind: "string"
    },
    {
      name: "minHeight",
      kind: "string"
    },
    {
      name: "minWidth",
      kind: "string"
    },
    {
      name: "position",
      kind: "json"
    },
    {
      name: "rtlEnabled",
      kind: "boolean"
    },
    {
      name: "shading",
      kind: "boolean"
    },
    {
      name: "shadingColor",
      kind: "string"
    },
    {
      name: "showIndicator",
      kind: "boolean"
    },
    {
      name: "showPane",
      kind: "boolean"
    },
    {
      name: "visible",
      kind: "boolean"
    },
    {
      name: "width",
      kind: "string"
    },
    {
      name: "wrapperAttr",
      kind: "json"
    }
  ]
}
