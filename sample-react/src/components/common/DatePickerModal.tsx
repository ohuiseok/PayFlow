import { useEffect, useState } from 'react';
import { Modal, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { colors } from '../common';

const MONTH_NAMES = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];
const DAY_NAMES = ['일', '월', '화', '수', '목', '금', '토'];

export function todayString(): string {
  const now = new Date();
  return [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-');
}

export function formatDateLabel(dateStr: string): string {
  if (!dateStr) return '';
  const [, m, d] = dateStr.split('-');
  const dayIdx = new Date(dateStr).getDay();
  return `${parseInt(m)}월 ${parseInt(d)}일 (${DAY_NAMES[dayIdx]})`;
}

type Props = {
  visible: boolean;
  selected: string; // "YYYY-MM-DD"
  onSelect: (date: string) => void;
  onClose: () => void;
};

export function DatePickerModal({ visible, selected, onSelect, onClose }: Props) {
  const [viewYear, setViewYear] = useState(() => parseInt(selected.slice(0, 4)));
  const [viewMonth, setViewMonth] = useState(() => parseInt(selected.slice(5, 7)) - 1);

  useEffect(() => {
    if (visible) {
      setViewYear(parseInt(selected.slice(0, 4)));
      setViewMonth(parseInt(selected.slice(5, 7)) - 1);
    }
  }, [visible, selected]);

  const prevMonth = () => {
    if (viewMonth === 0) { setViewMonth(11); setViewYear((y) => y - 1); }
    else setViewMonth((m) => m - 1);
  };

  const nextMonth = () => {
    if (viewMonth === 11) { setViewMonth(0); setViewYear((y) => y + 1); }
    else setViewMonth((m) => m + 1);
  };

  const firstDayOfWeek = new Date(viewYear, viewMonth, 1).getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const today = todayString();

  const cells: (number | null)[] = [];
  for (let i = 0; i < firstDayOfWeek; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);
  // pad to full rows
  while (cells.length % 7 !== 0) cells.push(null);

  return (
    <Modal transparent animationType="fade" visible={visible} onRequestClose={onClose}>
      <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose}>
        <TouchableOpacity activeOpacity={1} style={styles.panel}>
          {/* Month nav */}
          <View style={styles.monthRow}>
            <TouchableOpacity onPress={prevMonth} style={styles.arrowBtn} hitSlop={8}>
              <Text style={styles.arrow}>‹</Text>
            </TouchableOpacity>
            <Text style={styles.monthLabel}>
              {viewYear}년 {MONTH_NAMES[viewMonth]}
            </Text>
            <TouchableOpacity onPress={nextMonth} style={styles.arrowBtn} hitSlop={8}>
              <Text style={styles.arrow}>›</Text>
            </TouchableOpacity>
          </View>

          {/* Day-of-week headers */}
          <View style={styles.weekRow}>
            {DAY_NAMES.map((d, i) => (
              <Text key={d} style={[styles.dayHeader, i === 0 && styles.sun, i === 6 && styles.sat]}>
                {d}
              </Text>
            ))}
          </View>

          {/* Calendar grid */}
          <View style={styles.grid}>
            {cells.map((day, i) => {
              const col = i % 7;
              if (!day) {
                return <View key={`empty-${i}`} style={styles.cell} />;
              }
              const dateStr = `${viewYear}-${String(viewMonth + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
              const isSelected = dateStr === selected;
              const isToday = dateStr === today;

              return (
                <TouchableOpacity
                  key={dateStr}
                  style={[styles.cell, isSelected && styles.selectedCell]}
                  onPress={() => {
                    onSelect(dateStr);
                    onClose();
                  }}
                >
                  {isToday && !isSelected ? <View style={styles.todayDot} /> : null}
                  <Text
                    style={[
                      styles.dayText,
                      col === 0 && styles.sun,
                      col === 6 && styles.sat,
                      isSelected && styles.selectedText,
                      isToday && !isSelected && styles.todayText,
                    ]}
                  >
                    {day}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </TouchableOpacity>
      </TouchableOpacity>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(17, 24, 32, 0.38)',
    flex: 1,
    justifyContent: 'center',
    padding: 24,
  },
  panel: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    maxWidth: 340,
    padding: 20,
    width: '100%',
  },
  monthRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  arrowBtn: {
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  arrow: {
    color: colors.primary,
    fontSize: 26,
    fontWeight: '700',
    lineHeight: 28,
  },
  monthLabel: {
    color: colors.text,
    fontSize: 17,
    fontWeight: '900',
  },
  weekRow: {
    flexDirection: 'row',
    marginBottom: 4,
  },
  dayHeader: {
    color: colors.muted,
    flex: 1,
    fontSize: 12,
    fontWeight: '700',
    textAlign: 'center',
  },
  sun: {
    color: '#D84F45',
  },
  sat: {
    color: colors.blue,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  cell: {
    alignItems: 'center',
    aspectRatio: 1,
    justifyContent: 'center',
    width: `${100 / 7}%`,
    position: 'relative',
  },
  selectedCell: {
    backgroundColor: colors.primary,
    borderRadius: 999,
  },
  dayText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: '600',
  },
  selectedText: {
    color: '#FFFFFF',
    fontWeight: '900',
  },
  todayText: {
    color: colors.primary,
    fontWeight: '900',
  },
  todayDot: {
    backgroundColor: colors.primary,
    borderRadius: 999,
    bottom: 4,
    height: 4,
    position: 'absolute',
    width: 4,
  },
});
