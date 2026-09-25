import { SORT_LABELS, type ProductSort } from '../api/catalog'

const SORTS = Object.keys(SORT_LABELS) as ProductSort[]

export default function SortChips({ value, onChange }: { value: ProductSort; onChange: (s: ProductSort) => void }) {
  return (
    <div className="chips" role="radiogroup" aria-label="정렬">
      {SORTS.map((s) => (
        <button key={s} type="button" role="radio" aria-checked={value === s} className={`chip ${value === s ? 'active' : ''}`} onClick={() => onChange(s)}>
          {SORT_LABELS[s]}
        </button>
      ))}
    </div>
  )
}
