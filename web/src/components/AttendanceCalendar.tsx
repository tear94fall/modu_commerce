import { useState } from 'react'
import { datesBetween, dayOf, formatMonth, monthOf, monthsBetween, weekdayOf, WEEKDAYS } from '../util/calendar'

/** 기간이 이보다 길면(6주) 한 달씩 넘겨 본다. */
const MAX_DAYS_AT_ONCE = 42

interface AttendanceCalendarProps {
  startDate: string
  endDate: string
  today: string
  checkedDates: string[]
}

/**
 * 이벤트 기간의 출석 달력. 출석한 날은 도장, 오늘은 테두리, 아직 오지 않은 날은 흐리게.
 * 기간이 길면 오늘이 있는 달(기간 밖이면 가까운 끝 달)부터 보여 주고 이전/다음 달로 넘긴다.
 */
export default function AttendanceCalendar({ startDate, endDate, today, checkedDates }: AttendanceCalendarProps) {
  const all = datesBetween(startDate, endDate)
  const paged = all.length > MAX_DAYS_AT_ONCE
  const months = monthsBetween(startDate, endDate)
  const [month, setMonth] = useState(() => {
    const m = monthOf(today)
    if (m < months[0]) return months[0]
    if (m > months[months.length - 1]) return months[months.length - 1]
    return m
  })
  const monthIndex = months.indexOf(month)
  const dates = paged ? all.filter((d) => monthOf(d) === month) : all
  const checked = new Set(checkedDates)
  const leading = dates.length > 0 ? weekdayOf(dates[0]) : 0

  return (
    <div className="attend-cal">
      {paged && (
        <div className="attend-month">
          <button type="button" aria-label="이전 달" disabled={monthIndex <= 0} onClick={() => setMonth(months[monthIndex - 1])}>
            ‹
          </button>
          <span>{formatMonth(month)}</span>
          <button type="button" aria-label="다음 달" disabled={monthIndex >= months.length - 1} onClick={() => setMonth(months[monthIndex + 1])}>
            ›
          </button>
        </div>
      )}
      <div className="attend-grid" role="group" aria-label="출석 달력">
        {WEEKDAYS.map((w, i) => (
          <div key={w} className={`wd ${i === 0 ? 'sun' : i === 6 ? 'sat' : ''}`.trim()}>
            {w}
          </div>
        ))}
        {Array.from({ length: leading }, (_, i) => (
          <div key={`blank-${i}`} className="day blank" />
        ))}
        {dates.map((d) => {
          const isChecked = checked.has(d)
          const classes = ['day', isChecked && 'checked', d === today && 'today', d > today && 'future'].filter(Boolean).join(' ')
          const label = `${Number(d.slice(5, 7))}월 ${dayOf(d)}일${isChecked ? ' 출석' : ''}${d === today ? ' (오늘)' : ''}`
          return (
            <div key={d} className={classes} title={label} data-date={d}>
              <span className="num">{dayOf(d)}</span>
              {isChecked && (
                <svg className="stamp" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
                </svg>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
