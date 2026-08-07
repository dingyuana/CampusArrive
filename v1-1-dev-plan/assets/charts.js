(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var warn = style.getPropertyValue('--warn').trim();
  var danger = style.getPropertyValue('--danger').trim();
  var purple = style.getPropertyValue('--purple').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  /* ===== Chart 1: v1.1 迭代甘特图 ===== */
  var ganttEl = document.getElementById('chart-gantt');
  if (ganttEl) {
    var ganttChart = echarts.init(ganttEl, null, { renderer: 'svg' });

    var categories = [
      'AI 迎新智能助手',
      '系统集成中间件',
      '无障碍访问改造',
      '家长查看端',
      '安全与合规保障',
      '测试与上线'
    ];

    var phases = [
      { name: '环境搭建与 PoC', weeks: [1, 1], module: 0, color: accent },
      { name: '知识库与工作流', weeks: [2, 3], module: 0, color: accent },
      { name: '网关+队列+CDC', weeks: [2, 3], module: 1, color: accent2 },
      { name: '集成适配层', weeks: [4, 4], module: 1, color: accent2 },
      { name: 'ARIA + 高对比度', weeks: [3, 5], module: 2, color: warn },
      { name: 'CI/CD 集成', weeks: [5, 5], module: 2, color: warn },
      { name: 'H5 开发与绑定', weeks: [3, 5], module: 3, color: purple },
      { name: '威胁建模+架构评审', weeks: [1, 1], module: 4, color: danger },
      { name: 'PII 脱敏+AI 安全', weeks: [2, 3], module: 4, color: danger },
      { name: '数据分级+加密', weeks: [3, 4], module: 4, color: danger },
      { name: '渗透测试+等保', weeks: [5, 6], module: 4, color: danger },
      { name: '集成测试', weeks: [6, 6], module: 5, color: danger },
      { name: '压测与灰度上线', weeks: [6, 6], module: 5, color: danger }
    ];

    var data = [];
    phases.forEach(function (p) {
      data.push({
        name: p.name,
        value: [p.module, p.weeks[0] - 1, p.weeks[1], p.weeks[1] - p.weeks[0] + 1],
        itemStyle: { color: p.color }
      });
    });

    ganttChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: function (p) {
          return '<b>' + p.data.name + '</b><br/>Week ' + (p.data.value[1] + 1) + ' — Week ' + p.data.value[2] + '（' + p.data.value[3] + ' 周）';
        }
      },
      grid: { left: 130, right: 40, top: 20, bottom: 40 },
      xAxis: {
        type: 'value',
        min: 0,
        max: 6,
        interval: 1,
        axisLabel: {
          color: muted,
          fontSize: 12,
          formatter: function (v) { return v === 0 ? '' : 'W' + v; }
        },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'category',
        data: categories,
        axisLabel: { color: ink, fontSize: 13, fontWeight: 600 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false },
        inverse: true
      },
      series: [
        {
          type: 'custom',
          renderItem: function (params, api) {
            var catIdx = api.value(0);
            var start = api.coord([api.value(1), catIdx]);
            var end = api.coord([api.value(2), catIdx]);
            var height = api.size([0, 1])[1] * 0.5;
            return {
              type: 'rect',
              shape: {
                x: start[0],
                y: start[1] - height / 2,
                width: end[0] - start[0],
                height: height,
                r: 4
              },
              style: api.style()
            };
          },
          data: data,
          encode: { x: [1, 2], y: 0 },
          label: {
            show: true,
            position: 'inside',
            color: '#fff',
            fontSize: 11,
            fontWeight: 600,
            formatter: function (p) { return p.data.name; }
          }
        }
      ]
    });
    window.addEventListener('resize', function () { ganttChart.resize(); });
  }

  /* ===== Chart 2: 人力资源分配 ===== */
  var resEl = document.getElementById('chart-resource');
  if (resEl) {
    var resChart = echarts.init(resEl, null, { renderer: 'svg' });

    var weekLabels = ['Week 1', 'Week 2', 'Week 3', 'Week 4', 'Week 5', 'Week 6'];

    resChart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: {
        data: ['后端开发', '前端开发', '测试', 'DevOps'],
        bottom: 0,
        textStyle: { color: muted, fontSize: 12 },
        itemWidth: 14,
        itemHeight: 14,
        itemGap: 20
      },
      grid: { left: 50, right: 30, top: 20, bottom: 50 },
      xAxis: {
        type: 'category',
        data: weekLabels,
        axisLabel: { color: muted, fontSize: 12 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        name: '人天',
        nameTextStyle: { color: muted, fontSize: 11 },
        axisLabel: { color: muted, fontSize: 12 },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { show: false },
        axisTick: { show: false }
      },
      series: [
        {
          name: '后端开发',
          type: 'bar',
          stack: 'total',
          data: [10, 10, 10, 7, 5, 8],
          itemStyle: { color: accent, borderRadius: [0, 0, 0, 0] },
          barWidth: '40%'
        },
        {
          name: '前端开发',
          type: 'bar',
          stack: 'total',
          data: [5, 5, 5, 8, 10, 8],
          itemStyle: { color: accent2 }
        },
        {
          name: '测试',
          type: 'bar',
          stack: 'total',
          data: [2, 2, 2, 3, 5, 10],
          itemStyle: { color: warn }
        },
        {
          name: 'DevOps',
          type: 'bar',
          stack: 'total',
          data: [5, 4, 4, 2, 2, 4],
          itemStyle: { color: purple, borderRadius: [4, 4, 0, 0] }
        }
      ]
    });
    window.addEventListener('resize', function () { resChart.resize(); });
  }
})();
