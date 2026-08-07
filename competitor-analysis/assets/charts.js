(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var warn = style.getPropertyValue('--warn').trim();
  var danger = style.getPropertyValue('--danger').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  /* ===== Chart 1: 市场份额 ===== */
  var shareEl = document.getElementById('chart-share');
  if (shareEl) {
    var shareChart = echarts.init(shareEl);
    shareChart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: function (params) {
          var p = params[0];
          return p.name + '<br/>市场份额：<b>' + p.value + '%</b>';
        }
      },
      grid: { left: 90, right: 60, top: 20, bottom: 30 },
      xAxis: {
        type: 'value',
        max: 70,
        axisLabel: { color: muted, fontSize: 12, formatter: '{value}%' },
        splitLine: { lineStyle: { color: rule, type: 'dashed' } },
        axisLine: { show: false },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'category',
        data: ['其他厂商', '其他 TOP5 厂商', '新开普', '正方软件'],
        axisLabel: { color: ink, fontSize: 13, fontWeight: 600 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      series: [
        {
          type: 'bar',
          data: [
            { value: 43.6, itemStyle: { color: muted, opacity: 0.35 } },
            { value: 25.1, itemStyle: { color: warn, opacity: 0.6 } },
            { value: 14.2, itemStyle: { color: accent2 } },
            { value: 17.1, itemStyle: { color: accent } }
          ],
          barWidth: 28,
          label: {
            show: true,
            position: 'right',
            formatter: '{c}%',
            color: ink,
            fontSize: 13,
            fontWeight: 700
          },
          itemStyle: { borderRadius: [0, 6, 6, 0] }
        }
      ]
    });
    window.addEventListener('resize', function () { shareChart.resize(); });
  }

  /* ===== Chart 2: 竞争定位象限图 ===== */
  var posEl = document.getElementById('chart-positioning');
  if (posEl) {
    var posChart = echarts.init(posEl);
    posChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: function (p) {
          return '<b>' + p.data.name + '</b><br/>报到流程深度: ' + p.data.value[1] + '/10<br/>校园导航能力: ' + p.data.value[0] + '/10';
        }
      },
      grid: { left: 70, right: 50, top: 30, bottom: 60 },
      xAxis: {
        name: '校园导航能力 →',
        nameLocation: 'middle',
        nameGap: 38,
        nameTextStyle: { color: ink, fontSize: 13, fontWeight: 700 },
        min: -0.5,
        max: 10.5,
        interval: 2,
        axisLabel: { color: muted, fontSize: 11 },
        splitLine: {
          show: true,
          lineStyle: {
            color: function (val) { return val === 5 ? accent : rule; },
            type: function (val) { return val === 5 ? 'dashed' : 'solid'; },
            width: function (val) { return val === 5 ? 1.5 : 1; }
          }
        },
        axisLine: { lineStyle: { color: rule } }
      },
      yAxis: {
        name: '报到流程深度 →',
        nameLocation: 'middle',
        nameGap: 45,
        nameTextStyle: { color: ink, fontSize: 13, fontWeight: 700 },
        min: -0.5,
        max: 10.5,
        interval: 2,
        axisLabel: { color: muted, fontSize: 11 },
        splitLine: {
          show: true,
          lineStyle: {
            color: function (val) { return val === 5 ? accent : rule; },
            type: function (val) { return val === 5 ? 'dashed' : 'solid'; },
            width: function (val) { return val === 5 ? 1.5 : 1; }
          }
        },
        axisLine: { lineStyle: { color: rule } }
      },
      series: [
        {
          type: 'scatter',
          symbolSize: function (data) { return data.size || 22; },
          data: [
            { name: '我们', value: [9, 9], size: 32, itemStyle: { color: accent, borderColor: '#fff', borderWidth: 3, shadowBlur: 12, shadowColor: 'rgba(37,99,235,0.4)' }, label: { show: true, formatter: '我们', position: 'top', color: accent, fontSize: 14, fontWeight: 700 } },
            { name: '正方软件', value: [0.5, 8], itemStyle: { color: accent2 }, label: { show: true, formatter: '正方', position: 'left', color: ink, fontSize: 11 } },
            { name: '芒旭软件', value: [2, 8], itemStyle: { color: accent2 }, label: { show: true, formatter: '芒旭', position: 'left', color: ink, fontSize: 11 } },
            { name: '卓云科技', value: [0.5, 7.5], itemStyle: { color: accent2 }, label: { show: true, formatter: '卓云', position: 'bottom', color: ink, fontSize: 11 } },
            { name: '苏迪科技', value: [0.5, 7], itemStyle: { color: accent2 }, label: { show: true, formatter: '苏迪', position: 'left', color: ink, fontSize: 11 } },
            { name: '新开普', value: [0.5, 5], itemStyle: { color: warn }, label: { show: true, formatter: '新开普', position: 'left', color: ink, fontSize: 11 } },
            { name: '永拓科技', value: [9, 0.5], itemStyle: { color: accent2 }, label: { show: true, formatter: '永拓', position: 'right', color: ink, fontSize: 11 } },
            { name: '迅邦科技', value: [5, 0.5], itemStyle: { color: warn }, label: { show: true, formatter: '迅邦', position: 'top', color: ink, fontSize: 11 } }
          ],
          markArea: {
            silent: true,
            itemStyle: { color: 'rgba(37,99,235,0.06)' },
            data: [[{ xAxis: 5, yAxis: 5 }, { xAxis: 10.5, yAxis: 10.5 }]]
          },
          markLine: {
            silent: true,
            symbol: 'none',
            label: { show: false },
            data: [
              { xAxis: 5, lineStyle: { color: accent, type: 'dashed', width: 1.5 } },
              { yAxis: 5, lineStyle: { color: accent, type: 'dashed', width: 1.5 } }
            ]
          },
          markPoint: {
            symbol: 'rect',
            symbolSize: [120, 24],
            symbolOffset: [0, -2],
            itemStyle: { color: 'transparent' },
            label: {
              show: true,
              formatter: '市场空白区域',
              color: accent,
              fontSize: 11,
              fontWeight: 600,
              opacity: 0.7
            },
            data: [{ coord: [7.5, 7.5] }]
          }
        }
      ],
      graphic: [
        {
          type: 'text',
          right: 60,
          top: 40,
          style: { text: '高流程 + 高导航\n(无人占据)', fill: accent, fontSize: 11, opacity: 0.5, textAlign: 'right' }
        },
        {
          type: 'text',
          left: 80,
          top: 40,
          style: { text: '高流程 + 低导航\n(迎新专项厂商)', fill: muted, fontSize: 11, opacity: 0.5, textAlign: 'left' }
        },
        {
          type: 'text',
          right: 60,
          bottom: 70,
          style: { text: '低流程 + 高导航\n(导航专项厂商)', fill: muted, fontSize: 11, opacity: 0.5, textAlign: 'right' }
        },
        {
          type: 'text',
          left: 80,
          bottom: 70,
          style: { text: '低流程 + 低导航\n(待淘汰)', fill: muted, fontSize: 11, opacity: 0.5, textAlign: 'left' }
        }
      ]
    });
    window.addEventListener('resize', function () { posChart.resize(); });
  }
})();
