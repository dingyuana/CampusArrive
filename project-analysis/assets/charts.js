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

  /* ===== Chart 1: 各模块开发工作量与自研替代成本对比 ===== */
  var devEl = document.getElementById('chart-dev-effort');
  if (devEl) {
    var devChart = echarts.init(devEl, null, { renderer: 'svg' });

    devChart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: function (params) {
          var s = '<b>' + params[0].name + '</b>';
          params.forEach(function (p) {
            s += '<br/>' + p.marker + p.seriesName + ': ' + p.value + ' 人天';
          });
          return s;
        }
      },
      legend: {
        data: ['当前方案（开源集成）', '自研替代方案'],
        bottom: 0,
        textStyle: { color: muted, fontSize: 12 },
        itemWidth: 14,
        itemHeight: 14,
        itemGap: 20
      },
      grid: { left: 60, right: 30, top: 20, bottom: 50 },
      xAxis: {
        type: 'category',
        data: ['AI 助手', '集成中间件', '无障碍改造', '家长端'],
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
          name: '当前方案（开源集成）',
          type: 'bar',
          data: [20, 25, 15, 12],
          itemStyle: { color: accent, borderRadius: [4, 4, 0, 0] },
          barWidth: '30%',
          label: {
            show: true,
            position: 'top',
            color: accent,
            fontSize: 12,
            fontWeight: 600
          }
        },
        {
          name: '自研替代方案',
          type: 'bar',
          data: [60, 55, 35, 25],
          itemStyle: { color: warn, borderRadius: [4, 4, 0, 0] },
          barWidth: '30%',
          label: {
            show: true,
            position: 'top',
            color: warn,
            fontSize: 12,
            fontWeight: 600
          }
        }
      ]
    });
    window.addEventListener('resize', function () { devChart.resize(); });
  }

  /* ===== Chart 2: 关键链路响应时间瀑布图 ===== */
  var latEl = document.getElementById('chart-latency');
  if (latEl) {
    var latChart = echarts.init(latEl, null, { renderer: 'svg' });

    var latData = [
      { name: '新生签到', value: 350, color: accent2 },
      { name: 'AI 对话\n(首token)', value: 2000, color: warn },
      { name: 'AI 对话\n(完整回复)', value: 8000, color: warn },
      { name: '消息队列\n吞吐', value: 200, color: accent2 },
      { name: 'CDC 同步', value: 5000, color: accent2 },
      { name: '校园导航', value: 450, color: accent2 },
      { name: '家长端查询', value: 300, color: accent2 },
      { name: '缴费联动', value: 3000, color: warn }
    ];

    latChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: function (p) {
          return '<b>' + p.data.name.replace('\n', ' ') + '</b><br/>响应时间: ' + p.data.value + ' ms';
        }
      },
      grid: { left: 60, right: 30, top: 20, bottom: 50 },
      xAxis: {
        type: 'category',
        data: latData.map(function (d) { return d.name; }),
        axisLabel: { color: muted, fontSize: 10, interval: 0 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        name: 'ms',
        nameTextStyle: { color: muted, fontSize: 11 },
        axisLabel: { color: muted, fontSize: 12 },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { show: false },
        axisTick: { show: false }
      },
      series: [
        {
          type: 'bar',
          data: latData.map(function (d) {
            return { value: d.value, name: d.name, itemStyle: { color: d.color, borderRadius: [4, 4, 0, 0] } };
          }),
          barWidth: '45%',
          label: {
            show: true,
            position: 'top',
            color: ink,
            fontSize: 11,
            fontWeight: 600,
            formatter: function (p) {
              if (p.data.value >= 1000) {
                return (p.data.value / 1000).toFixed(1) + 's';
              }
              return p.data.value + 'ms';
            }
          },
          markLine: {
            data: [{ yAxis: 1000, name: '1s 目标线' }],
            lineStyle: { color: danger, type: 'dashed', width: 2 },
            label: { formatter: '1s 目标', color: danger, fontSize: 11, position: 'end' },
            symbol: 'none'
          }
        }
      ]
    });
    window.addEventListener('resize', function () { latChart.resize(); });
  }

  /* ===== Chart 3: 年度运行成本构成 ===== */
  var costEl = document.getElementById('chart-cost');
  if (costEl) {
    var costChart = echarts.init(costEl, null, { renderer: 'svg' });

    costChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: function (p) {
          return '<b>' + p.name + '</b><br/>' + p.value + ' 元/年 (' + p.percent + '%)';
        }
      },
      legend: {
        orient: 'vertical',
        right: 10,
        top: 'center',
        textStyle: { color: muted, fontSize: 12 },
        itemWidth: 14,
        itemHeight: 14,
        itemGap: 16,
        formatter: function (name) {
          var data = { '基础设施（电费）': 4800, 'DeepSeek API': 600, '短信验证码': 200 };
          return name + ': ' + data[name] + ' 元';
        }
      },
      series: [
        {
          type: 'pie',
          radius: ['45%', '70%'],
          center: ['35%', '50%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          label: {
            show: true,
            position: 'center',
            formatter: function () {
              return '{a|年度总成本}\n{b|~5,600 元}';
            },
            rich: {
              a: { color: muted, fontSize: 13, lineHeight: 24 },
              b: { color: accent, fontSize: 22, fontWeight: 700, lineHeight: 30 }
            }
          },
          emphasis: {
            label: { show: true, position: 'center' },
            itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.15)' }
          },
          data: [
            { value: 4800, name: '基础设施（电费）', itemStyle: { color: accent } },
            { value: 600, name: 'DeepSeek API', itemStyle: { color: accent2 } },
            { value: 200, name: '短信验证码', itemStyle: { color: warn } }
          ]
        }
      ]
    });
    window.addEventListener('resize', function () { costChart.resize(); });
  }

  /* ===== Chart 4: 技术风险矩阵 ===== */
  var riskEl = document.getElementById('chart-risk');
  if (riskEl) {
    var riskChart = echarts.init(riskEl, null, { renderer: 'svg' });

    var risks = [
      { name: 'DeepSeek API 涨价', prob: 3, impact: 2, color: warn },
      { name: 'MaxKB 集成问题', prob: 2, impact: 2, color: accent2 },
      { name: '老旧教务系统\n接口不标准', prob: 3, impact: 3, color: danger },
      { name: '无障碍改造\n成本超预期', prob: 2, impact: 2, color: accent2 },
      { name: '6 周排期紧凑', prob: 2, impact: 3, color: danger },
      { name: '消息队列\n数据一致性', prob: 2, impact: 3, color: danger },
      { name: '家长端\n隐私合规', prob: 1, impact: 3, color: accent2 }
    ];

    riskChart.setOption({
      tooltip: {
        formatter: function (p) {
          return '<b>' + p.data.name.replace('\n', ' ') + '</b><br/>概率: ' + p.data.prob + '/3<br/>影响: ' + p.data.impact + '/3';
        }
      },
      grid: { left: 60, right: 30, top: 30, bottom: 50 },
      xAxis: {
        type: 'value',
        name: '影响程度 →',
        nameLocation: 'middle',
        nameGap: 30,
        nameTextStyle: { color: muted, fontSize: 12 },
        min: 0,
        max: 4,
        interval: 1,
        axisLabel: { color: muted, fontSize: 11, formatter: function (v) { return ['', '低', '中', '高', ''][v] || ''; } },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        name: '发生概率 →',
        nameLocation: 'middle',
        nameGap: 40,
        nameTextStyle: { color: muted, fontSize: 12 },
        min: 0,
        max: 4,
        interval: 1,
        axisLabel: { color: muted, fontSize: 11, formatter: function (v) { return ['', '低', '中', '高', ''][v] || ''; } },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      series: [
        {
          type: 'scatter',
          symbolSize: function (data) { return 22; },
          data: risks.map(function (r) {
            return { value: [r.impact, r.prob], name: r.name, prob: r.prob, impact: r.impact, itemStyle: { color: r.color, opacity: 0.75, borderColor: r.color, borderWidth: 2 } };
          }),
          label: {
            show: true,
            formatter: function (p) { return p.data.name; },
            position: 'right',
            color: ink,
            fontSize: 11,
            fontWeight: 600,
            distance: 8
          }
        }
      ],
      visualMap: { show: false }
    });

    // Add quadrant background via graphic
    riskChart.setOption({
      graphic: [
        { type: 'text', left: '62%', top: '12%', style: { text: '高风险区', fill: danger, fontSize: 12, fontWeight: 600, opacity: 0.6 } },
        { type: 'text', left: '20%', top: '12%', style: { text: '监控区', fill: warn, fontSize: 12, fontWeight: 600, opacity: 0.6 } },
        { type: 'text', left: '20%', top: '75%', style: { text: '低风险区', fill: accent2, fontSize: 12, fontWeight: 600, opacity: 0.6 } },
        { type: 'text', left: '62%', top: '75%', style: { text: '关注区', fill: accent, fontSize: 12, fontWeight: 600, opacity: 0.6 } }
      ]
    });

    window.addEventListener('resize', function () { riskChart.resize(); });
  }

  /* ===== Chart 5: 数据安全合规差距评估雷达图 ===== */
  var secEl = document.getElementById('chart-security');
  if (secEl) {
    var secChart = echarts.init(secEl, null, { renderer: 'svg' });

    secChart.setOption({
      tooltip: {
        trigger: 'item'
      },
      legend: {
        data: ['当前状态', '合规目标'],
        bottom: 0,
        textStyle: { color: muted, fontSize: 12 },
        itemWidth: 14,
        itemHeight: 14,
        itemGap: 20
      },
      radar: {
        indicator: [
          { name: '数据加密存储', max: 10 },
          { name: '传输加密', max: 10 },
          { name: '访问控制', max: 10 },
          { name: '隐私政策', max: 10 },
          { name: 'AI 安全合规', max: 10 },
          { name: '等保备案', max: 10 },
          { name: '数据分类分级', max: 10 },
          { name: '审计日志', max: 10 },
          { name: 'PII 脱敏', max: 10 },
          { name: '应急响应', max: 10 }
        ],
        center: ['50%', '52%'],
        radius: '65%',
        axisName: { color: ink, fontSize: 11 },
        splitLine: { lineStyle: { color: rule } },
        splitArea: { areaStyle: { color: ['rgba(37,99,235,0.02)', 'rgba(37,99,235,0.05)'] } },
        axisLine: { lineStyle: { color: rule } }
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: [8, 9, 8, 2, 2, 0, 0, 7, 3, 3],
              name: '当前状态',
              itemStyle: { color: danger },
              lineStyle: { color: danger, width: 2 },
              areaStyle: { color: 'rgba(239,68,68,0.12)' },
              symbol: 'circle',
              symbolSize: 6
            },
            {
              value: [10, 10, 10, 10, 10, 10, 10, 10, 10, 10],
              name: '合规目标',
              itemStyle: { color: accent2 },
              lineStyle: { color: accent2, width: 2, type: 'dashed' },
              areaStyle: { color: 'rgba(16,185,129,0.06)' },
              symbol: 'circle',
              symbolSize: 6
            }
          ]
        }
      ]
    });
    window.addEventListener('resize', function () { secChart.resize(); });
  }
})();
