(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var warn = style.getPropertyValue('--warn').trim();

  // ===== Chart 1: Gantt — Dev Phase Timeline =====
  var ganttEl = document.getElementById('chart-gantt');
  if (ganttEl) {
    var ganttChart = echarts.init(ganttEl, null, { renderer: 'svg' });
    ganttChart.setOption({
      animation: false,
      tooltip: {
        trigger: 'item',
        appendToBody: true,
        formatter: function(p) {
          return p.data.name + '<br/>周次：第 ' + p.data.startWeek + ' - ' + p.data.endWeek + ' 周<br/>工期：' + (p.data.endWeek - p.data.startWeek + 1) + ' 周';
        }
      },
      grid: { left: 180, right: 40, top: 20, bottom: 40 },
      xAxis: {
        type: 'value',
        name: '周次',
        nameTextStyle: { color: muted, fontSize: 12 },
        min: 1, max: 8,
        splitLine: { lineStyle: { color: rule } },
        axisLabel: { color: muted, fontSize: 11 },
        axisLine: { lineStyle: { color: rule } }
      },
      yAxis: {
        type: 'category',
        data: ['阶段五：灰度上线', '阶段四：集成测试', '阶段三：辅导员+管理员端', '阶段二：新生端核心开发', '阶段一：需求与设计'],
        axisLabel: { color: ink, fontSize: 12 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      series: [{
        type: 'custom',
        renderItem: function(params, api) {
          var cat = api.value(0);
          var start = api.coord([api.value(1), cat]);
          var end = api.coord([api.value(2), cat]);
          var height = api.size([0, 1])[1] * 0.5;
          return {
            type: 'rect',
            shape: {
              x: start[0],
              y: start[1] - height / 2,
              width: end[0] - start[0],
              height: height
            },
            style: {
              fill: api.value(3),
              stroke: bg2,
              lineWidth: 2,
              borderRadius: 4
            }
          };
        },
        encode: { x: [1, 2], y: 0 },
        data: [
          { name: '需求与设计', value: [0, 1, 2, accent], startWeek: 1, endWeek: 2, itemStyle: { color: accent } },
          { name: '新生端核心开发', value: [1, 3, 5, accent], startWeek: 3, endWeek: 5, itemStyle: { color: accent } },
          { name: '辅导员+管理员端', value: [2, 6, 6, accent2], startWeek: 6, endWeek: 6, itemStyle: { color: accent2 } },
          { name: '集成测试', value: [3, 7, 7, warn], startWeek: 7, endWeek: 7, itemStyle: { color: warn } },
          { name: '灰度上线', value: [4, 8, 8, accent2], startWeek: 8, endWeek: 8, itemStyle: { color: accent2 } }
        ]
      }]
    });
    window.addEventListener('resize', function() { ganttChart.resize(); });
  }

  // ===== Chart 2: College Reporting Rate (Bar) =====
  var collegeEl = document.getElementById('chart-college');
  if (collegeEl) {
    var collegeChart = echarts.init(collegeEl, null, { renderer: 'svg' });
    collegeChart.setOption({
      animation: false,
      tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' } },
      grid: { left: 50, right: 30, top: 30, bottom: 60 },
      xAxis: {
        type: 'category',
        data: ['计算机学院', '机械学院', '外语学院', '经管学院', '医学院', '艺术学院', '理学院', '文学院'],
        axisLabel: { color: muted, fontSize: 11, rotate: 30 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        max: 100,
        axisLabel: { color: muted, fontSize: 11, formatter: '{value}%' },
        splitLine: { lineStyle: { color: rule } },
        axisLine: { show: false }
      },
      series: [{
        name: '报到率',
        type: 'bar',
        data: [88, 82, 75, 91, 68, 72, 85, 79],
        itemStyle: {
          color: function(params) {
            var val = params.value;
            if (val >= 85) return accent2;
            if (val >= 75) return accent;
            return warn;
          },
          borderRadius: [4, 4, 0, 0]
        },
        barWidth: '50%',
        label: { show: true, position: 'top', color: ink, fontSize: 11, formatter: '{c}%' }
      }]
    });
    window.addEventListener('resize', function() { collegeChart.resize(); });
  }

  // ===== Chart 3: Step Completion (Bar) =====
  var stepsEl = document.getElementById('chart-steps');
  if (stepsEl) {
    var stepsChart = echarts.init(stepsEl, null, { renderer: 'svg' });
    stepsChart.setOption({
      animation: false,
      tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' } },
      legend: { data: ['已完成', '进行中', '待办理'], bottom: 0, textStyle: { color: muted, fontSize: 12 } },
      grid: { left: 60, right: 30, top: 20, bottom: 50 },
      xAxis: {
        type: 'category',
        data: ['预登记', '到校签到', '身份核验', '缴费', '领物资', '宿舍入住', '学籍注册'],
        axisLabel: { color: muted, fontSize: 11 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        name: '人数',
        nameTextStyle: { color: muted, fontSize: 11 },
        axisLabel: { color: muted, fontSize: 11 },
        splitLine: { lineStyle: { color: rule } },
        axisLine: { show: false }
      },
      series: [
        {
          name: '已完成',
          type: 'bar',
          stack: 'total',
          data: [4930, 4250, 3856, 3500, 3100, 2750, 2400],
          itemStyle: { color: accent2, borderRadius: [0, 0, 0, 0] },
          barWidth: '45%'
        },
        {
          name: '进行中',
          type: 'bar',
          stack: 'total',
          data: [0, 680, 394, 356, 400, 350, 300],
          itemStyle: { color: accent }
        },
        {
          name: '待办理',
          type: 'bar',
          stack: 'total',
          data: [0, 0, 680, 1074, 1430, 1830, 2230],
          itemStyle: { color: rule, borderRadius: [4, 4, 0, 0] }
        }
      ]
    });
    window.addEventListener('resize', function() { stepsChart.resize(); });
  }

  // ===== Chart 4: Reporting Trend (Line) =====
  var trendEl = document.getElementById('chart-trend');
  if (trendEl) {
    var trendChart = echarts.init(trendEl, null, { renderer: 'svg' });
    trendChart.setOption({
      animation: false,
      tooltip: { trigger: 'axis', appendToBody: true },
      grid: { left: 50, right: 30, top: 20, bottom: 40 },
      xAxis: {
        type: 'category',
        data: ['8:00', '9:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00'],
        axisLabel: { color: muted, fontSize: 11 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        name: '报到人数',
        nameTextStyle: { color: muted, fontSize: 11 },
        axisLabel: { color: muted, fontSize: 11 },
        splitLine: { lineStyle: { color: rule } },
        axisLine: { show: false }
      },
      series: [{
        name: '报到人数',
        type: 'line',
        data: [120, 580, 920, 750, 430, 280, 690, 810, 520, 310],
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: accent, width: 3 },
        itemStyle: { color: accent },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: accent + '33' },
              { offset: 1, color: accent + '05' }
            ]
          }
        }
      }]
    });
    window.addEventListener('resize', function() { trendChart.resize(); });
  }
})();
