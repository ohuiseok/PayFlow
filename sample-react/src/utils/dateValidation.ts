const datePattern = /^\d{4}-\d{2}-\d{2}$/;

function toLocalDate(value: string) {
  if (!datePattern.test(value)) {
    return null;
  }

  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }

  date.setHours(0, 0, 0, 0);
  return date;
}

export function validateMissionDueDate(value: string, maxDays = 90) {
  const dueDate = toLocalDate(value);
  if (!dueDate) {
    return '날짜는 YYYY-MM-DD 형식으로 입력하세요.';
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const maxDate = new Date(today);
  maxDate.setDate(today.getDate() + maxDays);

  if (dueDate < today) {
    return '수행 날짜는 오늘 이후로 입력하세요.';
  }

  if (dueDate > maxDate) {
    return '수행 날짜는 오늘부터 90일 이내로 입력하세요.';
  }

  return '';
}
