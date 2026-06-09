import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import {
  AmountQuickSelect,
  BalanceCard,
  Body,
  Card,
  colors,
  FormField,
  formatAmountInput,
  formatWon,
  Heading,
  InfoBox,
  Label,
  parseAmount,
  PrimaryButton,
  ScreenFrame,
  SecondaryButton,
  StatusBadge,
} from '../components/common';
import { RootStackParamList } from '../navigation/routes';
import { useAppState } from '../state/AppState';
import { Mission, MissionStatus, UserRole } from '../types';

type Props<T extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, T>;

const missionStatusLabels: Record<MissionStatus, { label: string; tone: 'green' | 'blue' | 'yellow' | 'danger' }> = {
  todo: { label: '진행 중', tone: 'blue' },
  submitted: { label: '승인 대기', tone: 'yellow' },
  approved: { label: '승인', tone: 'green' },
  rejected: { label: '반려', tone: 'danger' },
  paid: { label: '지급 완료', tone: 'green' },
};

function missionLabel(status: MissionStatus) {
  return missionStatusLabels[status];
}

function RoleSwitch({ onSelect }: { onSelect: (role: UserRole) => void }) {
  return (
    <View style={styles.switchRow}>
      <TouchableOpacity style={styles.switchButton} onPress={() => onSelect('parent')}>
        <Text style={styles.switchText}>부모로 보기</Text>
      </TouchableOpacity>
      <TouchableOpacity style={styles.switchButton} onPress={() => onSelect('child')}>
        <Text style={styles.switchText}>자녀로 보기</Text>
      </TouchableOpacity>
    </View>
  );
}

function MissionCard({
  mission,
  onPress,
}: {
  mission: Mission;
  onPress?: () => void;
}) {
  const status = missionLabel(mission.status);
  return (
    <TouchableOpacity activeOpacity={onPress ? 0.75 : 1} onPress={onPress} style={styles.missionCard}>
      <View style={styles.rowBetween}>
        <Text style={styles.itemTitle}>{mission.title}</Text>
        <Text style={styles.reward}>{formatWon(mission.rewardAmount)}</Text>
      </View>
      <Text style={styles.itemMeta}>
        {mission.childName} · {mission.dueDate}
      </Text>
      <View style={styles.badgeLine}>
        <StatusBadge label={status.label} tone={status.tone} />
      </View>
    </TouchableOpacity>
  );
}

export function LoginScreen({ navigation }: Props<'Login'>) {
  const { loginAs, familyLinked } = useAppState();
  const [phone, setPhone] = useState('01012345678');
  const [password, setPassword] = useState('password12');
  const [error, setError] = useState('');

  const login = (role: UserRole) => {
    if (phone.replace(/[^0-9]/g, '').length < 10 || password.length < 8) {
      setError('휴대폰 번호와 8자리 이상 비밀번호를 입력하세요.');
      return;
    }

    setError('');
    loginAs(role);
    if (role === 'parent') {
      navigation.replace(familyLinked ? 'ParentHome' : 'ParentFamilyLink');
      return;
    }
    navigation.replace(familyLinked ? 'ChildHome' : 'ChildInviteCode');
  };

  return (
    <ScreenFrame>
      <View style={styles.loginCard}>
        <Text style={styles.brand}>PayFlow Family</Text>
        <Text style={styles.loginTitle}>미션으로 배우는 돈</Text>
        <Text style={styles.loginSub}>부모가 미션을 만들고 자녀가 보상을 벌어요.</Text>
        <View style={styles.spacer} />
        <FormField placeholder="휴대폰 번호" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
        <FormField
          placeholder="비밀번호"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          error={error}
        />
        <PrimaryButton title="로그인" onPress={() => login('parent')} />
        <TouchableOpacity style={styles.linkWrap} onPress={() => navigation.navigate('SignupRole')}>
          <Text style={styles.linkText}>
            처음이신가요? <Text style={styles.linkStrong}>회원가입</Text>
          </Text>
        </TouchableOpacity>
        <InfoBox title="역할 선택" body="로그인은 부모 계정으로 시작합니다. 아래 버튼으로 자녀 홈도 바로 확인할 수 있어요." />
        <RoleSwitch onSelect={login} />
      </View>
    </ScreenFrame>
  );
}

export function SignupRoleScreen({ navigation }: Props<'SignupRole'>) {
  const { signupAs } = useAppState();
  const [role, setRole] = useState<UserRole>('parent');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const canSubmit = name.trim().length > 1 && phone.replace(/[^0-9]/g, '').length >= 10;

  const submit = () => {
    signupAs(role, name);
    navigation.replace(role === 'parent' ? 'ParentFamilyLink' : 'ChildInviteCode');
  };

  return (
    <ScreenFrame eyebrow="회원가입" title="어떤 계정인가요?" description="역할에 따라 홈 화면과 권한이 달라집니다.">
      <View style={styles.roleCards}>
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={() => setRole('parent')}
          style={[styles.roleCard, role === 'parent' && styles.roleSelected]}
        >
          <Text style={styles.roleLabel}>부모 계정</Text>
          <Heading>미션을 만들고 보상 지급</Heading>
          <Body>크레딧 충전, 자녀 연결, 제출 승인</Body>
          {role === 'parent' ? <Text style={styles.check}>✓</Text> : null}
        </TouchableOpacity>
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={() => setRole('child')}
          style={[styles.roleCard, role === 'child' && styles.roleSelected]}
        >
          <Text style={styles.roleLabel}>자녀 계정</Text>
          <Heading>미션을 완료하고 돈 벌기</Heading>
          <Body>미션 확인, 완료 제출, 캐시북 기록</Body>
          {role === 'child' ? <Text style={styles.check}>✓</Text> : null}
        </TouchableOpacity>
      </View>
      <FormField placeholder="이름" value={name} onChangeText={setName} />
      <FormField placeholder="휴대폰 번호" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
      <PrimaryButton title="다음으로" onPress={submit} disabled={!canSubmit} />
    </ScreenFrame>
  );
}

export function ParentFamilyLinkScreen({ navigation }: Props<'ParentFamilyLink'>) {
  const { completeFamilyLink, inviteCode } = useAppState();
  const [requested, setRequested] = useState(false);

  const approve = () => {
    completeFamilyLink();
    navigation.replace('ParentHome');
  };

  return (
    <ScreenFrame eyebrow="가족 연결" title="자녀와 연결하기" description="초대 코드로 부모와 자녀 관계를 확인합니다.">
      <BalanceCard label="부모 초대 코드" amount={0} description={inviteCode} />
      <Card>
        <Heading>연결 대기 중</Heading>
        <Body>민지가 코드를 입력하면 승인 요청이 표시됩니다.</Body>
      </Card>
      <Card tone={requested ? 'green' : 'yellow'}>
        <Label>{requested ? '요청 도착' : '요청 시뮬레이션'}</Label>
        <Heading>민지 · 자녀 계정</Heading>
        <Body>{requested ? '가족으로 연결할까요?' : '자녀 앱에서 코드를 입력한 상태를 만들어봅니다.'}</Body>
      </Card>
      {requested ? (
        <View style={styles.twoButtons}>
          <PrimaryButton title="승인" onPress={approve} />
          <SecondaryButton title="거절" onPress={() => setRequested(false)} />
        </View>
      ) : (
        <PrimaryButton title="요청 도착시키기" onPress={() => setRequested(true)} />
      )}
    </ScreenFrame>
  );
}

export function ChildInviteCodeScreen({ navigation }: Props<'ChildInviteCode'>) {
  const { completeFamilyLink } = useAppState();
  const [code, setCode] = useState('PF4829');
  const [requested, setRequested] = useState(false);
  const valid = code.replace(/[^A-Za-z0-9]/g, '').length === 6;

  const request = () => {
    if (!requested) {
      setRequested(true);
      return;
    }
    completeFamilyLink();
    navigation.replace('ChildHome');
  };

  return (
    <ScreenFrame eyebrow="자녀 가족 연결" title="초대 코드 입력" description="부모님 앱에 표시된 코드를 입력하세요.">
      <InfoBox tone="blue" title="연결 방법" body="부모 초대 코드 6자리를 입력하면 부모 승인 후 가족으로 연결됩니다." />
      <View style={styles.codeRow}>
        {code.padEnd(6, ' ').slice(0, 6).split('').map((char, index) => (
          <View key={`${char}-${index}`} style={styles.codeCell}>
            <Text style={styles.codeText}>{char.trim() || '-'}</Text>
          </View>
        ))}
      </View>
      <FormField placeholder="초대 코드 6자리" value={code} onChangeText={setCode} />
      <Card>
        <Heading>부모 정보 확인</Heading>
        <Body>지훈님 가족으로 연결 요청을 보냅니다.</Body>
      </Card>
      <InfoBox tone="yellow" title="요청 후 상태" body={requested ? '요청 완료 · 부모 승인 대기' : '아직 요청하지 않았습니다.'} />
      <PrimaryButton title={requested ? '승인 완료로 이동' : '연결 요청 보내기'} onPress={request} disabled={!valid} />
    </ScreenFrame>
  );
}

export function ParentHomeScreen({ navigation }: Props<'ParentHome'>) {
  const { loginAs, missions, parentCreditBalance } = useAppState();
  const pending = missions.filter((mission) => mission.status === 'submitted');
  const active = missions.filter((mission) => mission.status !== 'paid');

  return (
    <ScreenFrame eyebrow="부모 홈" title="오늘의 보상 흐름" description="자녀 미션과 크레딧 상태를 한 번에 확인합니다.">
      <BalanceCard
        label="보상 크레딧"
        amount={parentCreditBalance}
        description={`승인 대기 ${pending.length}건 · 진행 미션 ${active.length}건`}
      />
      <View style={styles.actionGrid}>
        <PrimaryButton title="충전" onPress={() => navigation.navigate('CreditCharge')} />
        <SecondaryButton title="미션 등록" onPress={() => navigation.navigate('MissionCreate')} />
        <SecondaryButton title="승인" onPress={() => navigation.navigate('ParentApproval')} />
        <SecondaryButton title="자녀 홈" onPress={() => { loginAs('child'); navigation.navigate('ChildHome'); }} />
      </View>
      <Text style={styles.sectionTitle}>진행 중 미션</Text>
      {active.length ? active.map((mission) => <MissionCard key={mission.id} mission={mission} />) : <Body>진행 중인 미션이 없습니다.</Body>}
    </ScreenFrame>
  );
}

export function CreditChargeScreen({ navigation }: Props<'CreditCharge'>) {
  const { chargeCredit, parentCreditBalance } = useAppState();
  const [amountText, setAmountText] = useState('30000');
  const [status, setStatus] = useState<'idle' | 'processing' | 'completed' | 'failed'>('idle');
  const amount = parseAmount(amountText);
  const valid = amount >= 10000 && amount <= 1000000;

  const charge = () => {
    if (!valid) return;
    setStatus('processing');
    setTimeout(() => {
      chargeCredit(amount);
      setStatus('completed');
    }, 700);
  };

  return (
    <ScreenFrame eyebrow="크레딧 충전" title="보상 지갑 채우기" description="부모 계좌에서 보상 크레딧을 충전합니다.">
      <BalanceCard label="현재 보상 크레딧" amount={parentCreditBalance} description="충전 후 미션 승인에 사용할 수 있습니다." />
      <FormField
        label="충전 금액"
        placeholder="10,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        error={amountText && !valid ? '10,000원부터 1,000,000원까지 충전할 수 있습니다.' : undefined}
      />
      <AmountQuickSelect amounts={[10000, 30000, 50000]} onSelect={(value) => setAmountText(String(value))} />
      <InfoBox
        tone={status === 'failed' ? 'yellow' : 'green'}
        title={status === 'processing' ? '처리 중' : status === 'completed' ? '완료' : '예상 잔액'}
        body={status === 'completed' ? `충전 완료 · ${formatWon(parentCreditBalance)}` : `${formatWon(parentCreditBalance + (valid ? amount : 0))}`}
      />
      <PrimaryButton title={status === 'processing' ? '처리 중' : '충전하기'} onPress={charge} disabled={!valid || status === 'processing'} loading={status === 'processing'} />
      {status === 'completed' ? <SecondaryButton title="부모 홈으로" onPress={() => navigation.navigate('ParentHome')} /> : null}
    </ScreenFrame>
  );
}

export function MissionCreateScreen({ navigation }: Props<'MissionCreate'>) {
  const { createMission, parentCreditBalance } = useAppState();
  const [title, setTitle] = useState('영어 단어 20개 외우기');
  const [description, setDescription] = useState('단어장 사진과 암기 결과를 올려주세요.');
  const [amountText, setAmountText] = useState('5000');
  const [dueDate, setDueDate] = useState('2026-06-30');
  const amount = parseAmount(amountText);
  const valid = title.trim() && amount >= 1000 && amount <= parentCreditBalance;

  const submit = () => {
    createMission({ title, description, rewardAmount: amount, dueDate });
    navigation.navigate('ParentHome');
  };

  return (
    <ScreenFrame eyebrow="미션 등록" title="새 미션 만들기" description="자녀에게 할 일과 보상 금액을 보냅니다.">
      <InfoBox title="대상 자녀" body="민지에게 미션을 보냅니다." />
      <FormField label="미션 이름" placeholder="예: 수학 문제집 3쪽" value={title} onChangeText={setTitle} />
      <FormField label="조건 안내" placeholder="완료 기준" value={description} onChangeText={setDescription} />
      <FormField label="수행 날짜" placeholder="YYYY-MM-DD" value={dueDate} onChangeText={setDueDate} />
      <FormField
        label="보상 금액"
        placeholder="1,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        error={amount > parentCreditBalance ? '보상 크레딧보다 큰 금액은 등록할 수 없습니다.' : undefined}
      />
      <PrimaryButton title="미션 등록" onPress={submit} disabled={!valid} />
    </ScreenFrame>
  );
}

export function ChildHomeScreen({ navigation }: Props<'ChildHome'>) {
  const { cashbookEntries, childCashBalance, loginAs, missions } = useAppState();
  const todo = missions.filter((mission) => mission.status === 'todo');
  const rejected = missions.filter((mission) => mission.status === 'rejected');

  return (
    <ScreenFrame eyebrow="자녀 홈" title="내 미션과 캐시북" description="받을 수 있는 보상과 최근 기록을 확인합니다.">
      <BalanceCard label="내 지갑 잔액" amount={childCashBalance} description={`진행 가능 ${todo.length}건 · 반려 ${rejected.length}건`} />
      <View style={styles.actionGrid}>
        <PrimaryButton title="계좌 등록" onPress={() => navigation.navigate('BankAccountRegister')} />
        <SecondaryButton title="출금" onPress={() => navigation.navigate('ChildWithdrawal')} />
        <SecondaryButton title="부모 홈" onPress={() => { loginAs('parent'); navigation.navigate('ParentHome'); }} />
      </View>
      <Text style={styles.sectionTitle}>미션</Text>
      {missions.map((mission) => (
        <MissionCard
          key={mission.id}
          mission={mission}
          onPress={() =>
            navigation.navigate(mission.status === 'rejected' ? 'RejectResubmit' : 'MissionSubmit', { missionId: mission.id })
          }
        />
      ))}
      <Text style={styles.sectionTitle}>최근 캐시북</Text>
      {cashbookEntries.map((entry) => (
        <Card key={entry.id}>
          <View style={styles.rowBetween}>
            <View>
              <Text style={styles.itemTitle}>{entry.title}</Text>
              <Text style={styles.itemMeta}>{entry.description}</Text>
            </View>
            <Text style={[styles.amount, entry.amount < 0 && styles.negative]}>{entry.amount > 0 ? '+' : ''}{formatWon(entry.amount)}</Text>
          </View>
        </Card>
      ))}
    </ScreenFrame>
  );
}

export function MissionSubmitScreen({ navigation, route }: Props<'MissionSubmit'>) {
  const { missions, submitMission } = useAppState();
  const mission = missions.find((item) => item.id === route.params?.missionId) ?? missions.find((item) => item.status === 'todo') ?? missions[0];
  const [memo, setMemo] = useState('완료 사진을 첨부했어요.');

  return (
    <ScreenFrame eyebrow="완료 제출" title="미션 완료 알리기" description="완료한 내용을 부모에게 제출합니다.">
      <MissionCard mission={mission} />
      <InfoBox tone="blue" title="사진 첨부" body="MVP에서는 사진 URL placeholder로 제출합니다." />
      <FormField label="제출 메모" placeholder="완료 내용을 적어주세요." value={memo} onChangeText={setMemo} />
      <PrimaryButton
        title="제출하기"
        onPress={() => {
          submitMission(mission.id, memo);
          navigation.navigate('ChildHome');
        }}
        disabled={!memo.trim()}
      />
    </ScreenFrame>
  );
}

export function ParentApprovalScreen({ navigation }: Props<'ParentApproval'>) {
  const { approveMission, missions, rejectMission } = useAppState();
  const pending = missions.filter((mission) => mission.status === 'submitted');
  const [reason, setReason] = useState('조금 더 선명한 사진으로 다시 올려주세요.');
  const [message, setMessage] = useState('');
  const selected = pending[0];

  return (
    <ScreenFrame eyebrow="제출 확인" title="승인할 미션" description="자녀가 제출한 내용을 확인하고 보상을 지급합니다.">
      {selected ? (
        <>
          <MissionCard mission={selected} />
          <Card>
            <Label>제출 메모</Label>
            <Heading>{selected.submitMemo || '제출 메모가 없습니다.'}</Heading>
            <Body>승인하면 부모 크레딧이 차감되고 자녀 캐시북에 보상이 기록됩니다.</Body>
          </Card>
          <FormField label="반려 사유" placeholder="반려 사유" value={reason} onChangeText={setReason} />
          {message ? <InfoBox tone="yellow" title="처리 결과" body={message} /> : null}
          <View style={styles.twoButtons}>
            <PrimaryButton
              title="승인"
              onPress={() => {
                const ok = approveMission(selected.id);
                setMessage(ok ? '승인 완료 · 보상이 지급되었습니다.' : '크레딧 잔액이 부족합니다.');
              }}
            />
            <SecondaryButton title="반려" onPress={() => { rejectMission(selected.id, reason); navigation.navigate('ParentHome'); }} />
          </View>
        </>
      ) : (
        <>
          <InfoBox title="승인 대기 없음" body="현재 제출된 미션이 없습니다." />
          <SecondaryButton title="부모 홈으로" onPress={() => navigation.navigate('ParentHome')} />
        </>
      )}
    </ScreenFrame>
  );
}

export function RejectResubmitScreen({ navigation, route }: Props<'RejectResubmit'>) {
  const { missions, resubmitMission } = useAppState();
  const mission = missions.find((item) => item.id === route.params?.missionId) ?? missions.find((item) => item.status === 'rejected') ?? missions[0];
  const [memo, setMemo] = useState('빠진 부분까지 다시 완료했어요.');

  return (
    <ScreenFrame eyebrow="반려 재제출" title="다시 제출하기" description="반려 사유를 확인하고 보완 내용을 보냅니다.">
      <MissionCard mission={mission} />
      <InfoBox tone="yellow" title="반려 사유" body={mission.rejectReason || '보완이 필요합니다.'} />
      <FormField label="재제출 메모" placeholder="보완 내용을 적어주세요." value={memo} onChangeText={setMemo} />
      <PrimaryButton
        title="재제출"
        onPress={() => {
          resubmitMission(mission.id, memo);
          navigation.navigate('ChildHome');
        }}
        disabled={!memo.trim()}
      />
    </ScreenFrame>
  );
}

export function BankAccountRegisterScreen({ navigation }: Props<'BankAccountRegister'>) {
  const { linkedBankAccount, registerBankAccount } = useAppState();
  const [bankName, setBankName] = useState(linkedBankAccount?.bankName ?? '국민은행');
  const [accountNumber, setAccountNumber] = useState(linkedBankAccount?.accountNumber ?? '123456789012');
  const [holderName, setHolderName] = useState(linkedBankAccount?.holderName ?? '민지');
  const [done, setDone] = useState(false);
  const valid = /^\d{10,14}$/.test(accountNumber.replace(/[^0-9]/g, ''));

  return (
    <ScreenFrame eyebrow="계좌 등록" title="받을 계좌 연결" description="출금 받을 계좌를 등록합니다.">
      <InfoBox tone="blue" title="등록 가능" body="본인 명의 계좌만 등록할 수 있습니다." />
      <FormField label="은행" placeholder="은행 선택" value={bankName} onChangeText={setBankName} />
      <FormField
        label="계좌번호"
        placeholder="숫자만 10~14자리"
        value={accountNumber}
        onChangeText={(value) => setAccountNumber(value.replace(/[^0-9]/g, ''))}
        keyboardType="number-pad"
        error={accountNumber && !valid ? '계좌번호는 숫자 10~14자리로 입력하세요.' : undefined}
      />
      <FormField label="예금주" placeholder="예금주" value={holderName} onChangeText={setHolderName} />
      {done ? <InfoBox title="등록 완료" body={`${bankName} ${accountNumber} 계좌가 연결되었습니다.`} /> : null}
      <PrimaryButton
        title="계좌 등록"
        onPress={() => {
          registerBankAccount({ bankName, accountNumber, holderName });
          setDone(true);
        }}
        disabled={!valid || !bankName.trim() || !holderName.trim()}
      />
      {done ? <SecondaryButton title="출금 화면으로" onPress={() => navigation.navigate('ChildWithdrawal')} /> : null}
    </ScreenFrame>
  );
}

export function ChildWithdrawalScreen({ navigation }: Props<'ChildWithdrawal'>) {
  const { childCashBalance, linkedBankAccount, withdrawCash } = useAppState();
  const [amountText, setAmountText] = useState('5000');
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState('');
  const amount = parseAmount(amountText);
  const valid = amount >= 1000 && amount <= childCashBalance;

  const withdraw = () => {
    const ok = withdrawCash(amount);
    setConfirming(false);
    setMessage(ok ? '출금 완료 · 잔액이 차감되었습니다.' : '출금 가능 금액을 초과했습니다.');
  };

  return (
    <ScreenFrame eyebrow="계좌 출금" title="캐시북에서 출금" description="모은 보상을 등록 계좌로 보냅니다.">
      <BalanceCard label="출금 가능 잔액" amount={childCashBalance} description="요청 후 처리 중 상태를 거쳐 완료됩니다." />
      <Card>
        <Label>받을 계좌</Label>
        <Heading>{linkedBankAccount ? `${linkedBankAccount.bankName} ${linkedBankAccount.accountNumber}` : '등록된 계좌 없음'}</Heading>
        <Body>{linkedBankAccount ? linkedBankAccount.holderName : '계좌를 먼저 등록하세요.'}</Body>
      </Card>
      <FormField
        label="출금 금액"
        placeholder="1,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        error={amountText && !valid ? '잔액 안에서 1,000원 이상 출금할 수 있습니다.' : undefined}
      />
      <AmountQuickSelect amounts={[5000, 10000, 30000]} onSelect={(value) => setAmountText(String(value))} />
      <InfoBox tone="yellow" title="주의" body="출금 요청 중에는 같은 요청을 다시 보낼 수 없습니다." />
      {message ? <InfoBox title="처리 결과" body={message} /> : null}
      {confirming ? (
        <Card>
          <Heading>{formatWon(amount)} 출금할까요?</Heading>
          <Body>확인하면 자녀 지갑 잔액에서 바로 차감됩니다.</Body>
          <View style={styles.twoButtons}>
            <PrimaryButton title="진행" onPress={withdraw} />
            <SecondaryButton title="취소" onPress={() => setConfirming(false)} />
          </View>
        </Card>
      ) : (
        <PrimaryButton
          title={linkedBankAccount ? '출금 요청' : '계좌 등록하기'}
          onPress={() => (linkedBankAccount ? setConfirming(true) : navigation.navigate('BankAccountRegister'))}
          disabled={linkedBankAccount ? !valid : false}
        />
      )}
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  loginCard: {
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    marginTop: 30,
    padding: 24,
  },
  brand: {
    color: colors.primary,
    fontSize: 15,
    fontWeight: '900',
    marginBottom: 16,
  },
  loginTitle: {
    color: colors.text,
    fontSize: 32,
    fontWeight: '900',
    lineHeight: 39,
  },
  loginSub: {
    color: colors.muted,
    fontSize: 16,
    marginTop: 8,
  },
  spacer: {
    height: 34,
  },
  linkWrap: {
    alignItems: 'center',
    paddingVertical: 22,
  },
  linkText: {
    color: colors.muted,
    fontSize: 16,
  },
  linkStrong: {
    color: colors.primary,
    fontWeight: '900',
  },
  switchRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 10,
  },
  switchButton: {
    alignItems: 'center',
    backgroundColor: '#EEF1F4',
    borderRadius: 8,
    flex: 1,
    minHeight: 44,
    justifyContent: 'center',
  },
  switchText: {
    color: colors.dark,
    fontSize: 13,
    fontWeight: '900',
  },
  roleCards: {
    gap: 14,
    marginBottom: 20,
  },
  roleCard: {
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    padding: 20,
    position: 'relative',
  },
  roleSelected: {
    backgroundColor: colors.primarySoft,
    borderColor: '#BFE8D4',
  },
  roleLabel: {
    color: colors.primary,
    fontSize: 16,
    fontWeight: '900',
    marginBottom: 12,
  },
  check: {
    backgroundColor: colors.primary,
    borderRadius: 999,
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '900',
    height: 28,
    lineHeight: 28,
    position: 'absolute',
    right: 18,
    textAlign: 'center',
    top: 18,
    width: 28,
  },
  twoButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  codeRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 22,
  },
  codeCell: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    flex: 1,
    height: 58,
    justifyContent: 'center',
  },
  codeText: {
    color: colors.text,
    fontSize: 24,
    fontWeight: '900',
  },
  rowBetween: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
    justifyContent: 'space-between',
  },
  actionGrid: {
    gap: 12,
    marginBottom: 24,
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 19,
    fontWeight: '900',
    marginBottom: 12,
    marginTop: 6,
  },
  missionCard: {
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 12,
    padding: 18,
  },
  itemTitle: {
    color: colors.text,
    flex: 1,
    fontSize: 17,
    fontWeight: '900',
  },
  itemMeta: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 6,
  },
  reward: {
    color: colors.primary,
    fontSize: 15,
    fontWeight: '900',
  },
  badgeLine: {
    marginTop: 12,
  },
  amount: {
    color: colors.primary,
    fontSize: 16,
    fontWeight: '900',
  },
  negative: {
    color: colors.danger,
  },
});
