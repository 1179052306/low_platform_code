/**
 * DevExtreme 图表配置 → ECharts option 转换
 */

/** 把 DevExtreme Chart 配置转为 ECharts option */
export function dxChartToEcharts(props: any): any {
  const option: any = {
    title: props.title ? { text: typeof props.title === 'string' ? props.title : props.title.text } : undefined,
    tooltip: { trigger: 'item' },
    legend: props.legend !== false ? {} : undefined,
  }

  const series = props.series || []
  const dataSource = Array.isArray(props.dataSource) ? props.dataSource : []

  // argumentField / valueField 提取
  const argField = props.argumentField || 'arg'
  const valField = series[0]?.valueField || 'val'

  option.series = series.map((s: any) => {
    const type = mapSeriesType(s.type || props.type || 'bar')
    return {
      name: s.name || s.title || valField,
      type,
      data: dataSource.map((item: any) => [item[argField], item[s.valueField || valField]]),
      ...(s.color ? { itemStyle: { color: s.color } } : {}),
    }
  })

  // 坐标轴
  if (props.argumentAxis) {
    option.xAxis = { type: 'category', name: props.argumentAxis?.title?.text }
  } else {
    option.xAxis = { type: 'category' }
  }
  if (props.valueAxis) {
    option.yAxis = { type: 'value', name: props.valueAxis?.title?.text }
  } else {
    option.yAxis = { type: 'value' }
  }

  return option
}

function mapSeriesType(dxType: string): string {
  switch (dxType) {
    case 'bar': return 'bar'
    case 'line': return 'line'
    case 'area': return 'line'
    case 'scatter': return 'scatter'
    case 'pie': return 'pie'
    case 'bubble': return 'scatter'
    default: return 'bar'
  }
}

/** DevExtreme PieChart → ECharts pie option */
export function dxPieToEcharts(props: any): any {
  const dataSource = Array.isArray(props.dataSource) ? props.dataSource : []
  const argField = props.argumentField || 'arg'
  const valField = props.valueField || 'val'
  return {
    title: props.title ? { text: typeof props.title === 'string' ? props.title : props.title.text } : undefined,
    tooltip: { trigger: 'item' },
    legend: props.legend !== false ? { bottom: 0 } : undefined,
    series: [{
      type: 'pie',
      radius: props.type === 'doughnut' ? ['40%', '70%'] : '70%',
      data: dataSource.map((item: any) => ({ name: item[argField], value: item[valField] })),
    }],
  }
}

/** DevExtreme Gauge → ECharts gauge option */
export function dxGaugeToEcharts(props: any): any {
  const value = props.value ?? 0
  const max = props.max ?? 100
  const min = props.min ?? 0
  return {
    series: [{
      type: 'gauge',
      min,
      max,
      data: [{ value }],
    }],
  }
}

/** DevExtreme Funnel → ECharts funnel option */
export function dxFunnelToEcharts(props: any): any {
  const dataSource = Array.isArray(props.dataSource) ? props.dataSource : []
  const argField = props.argumentField || 'arg'
  const valField = props.valueField || 'val'
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'funnel',
      data: dataSource.map((item: any) => ({ name: item[argField], value: item[valField] })),
    }],
  }
}

/** DevExtreme Sankey → ECharts sankey option */
export function dxSankeyToEcharts(props: any): any {
  return {
    series: [{
      type: 'sankey',
      data: props.nodes || [],
      links: props.links || [],
    }],
  }
}

/** DevExtreme TreeMap → ECharts treemap option */
export function dxTreeMapToEcharts(props: any): any {
  return {
    series: [{
      type: 'treemap',
      data: props.dataSource || [],
    }],
  }
}