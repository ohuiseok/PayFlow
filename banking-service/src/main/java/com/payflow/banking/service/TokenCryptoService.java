package com.payflow.banking.service;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenBanking 액세스 토큰을 AES-256-GCM으로 암호화/복호화한다.
 *
 * <p>[M-7] 키 유도를 SHA-256에서 PBKDF2WithHmacSHA256으로 강화했다.
 * SHA-256은 단순 해시로 키를 만들어 브루트포스 공격에 취약하다.
 * PBKDF2는 반복 연산(65536회)으로 키 유도 비용을 높여 사전 공격을 어렵게 만든다.</p>
 *
 * <p>점진적 마이그레이션:
 * 기존에 SHA-256으로 암호화된 토큰이 DB에 남아 있을 수 있다.
 * {@code decrypt()} 는 PBKDF2 키로 먼저 복호화를 시도하고,
 * 실패하면 레거시 SHA-256 키로 fallback 복호화를 시도한다.
 * 토큰 재발급 시 새로운 PBKDF2 키로 자동 업데이트되므로 별도 배치 없이 점진 마이그레이션이 가능하다.</p>
 */
@Component
public class TokenCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String LOCAL_DEFAULT_SECRET = "payflow-local-token-secret";
    // PBKDF2 파라미터: salt는 서비스 내부 고정값을 사용한다.
    // 각 토큰 암호화에 개별 salt를 쓰면 더 안전하지만, 현재 스키마(토큰 단일 컬럼)를 변경하지 않으려면 고정 salt가 현실적이다.
    private static final byte[] PBKDF2_SALT = "payflow-openbanking-kdf-salt-v1".getBytes(StandardCharsets.UTF_8);
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int PBKDF2_KEY_LENGTH = 256;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;
    private final SecretKeySpec legacyKeySpec;
    private final String encryptionSecret;
    private final Environment environment;

    public TokenCryptoService(
            @Value("${openbanking.token-encryption-secret:${internal.secret:payflow-local-token-secret}}") String secret,
            Environment environment
    ) {
        this.encryptionSecret = StringUtils.hasText(secret) ? secret : LOCAL_DEFAULT_SECRET;
        this.environment = environment;
        // [M-7] 신규 키: PBKDF2WithHmacSHA256 유도
        this.keySpec = new SecretKeySpec(pbkdf2(this.encryptionSecret), "AES");
        // 점진적 마이그레이션을 위한 레거시 키: 기존 SHA-256 방식
        this.legacyKeySpec = new SecretKeySpec(sha256(this.encryptionSecret), "AES");
    }

    // [H-3] 운영 프로파일에서 기본 로컬 키가 사용되면 시작을 거부한다.
    // 기본 키로 암호화된 토큰은 소스코드를 아는 누구나 복호화할 수 있어 보안 위협이 된다.
    @PostConstruct
    void validateEncryptionKey() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd && LOCAL_DEFAULT_SECRET.equals(encryptionSecret)) {
            throw new IllegalStateException(
                    "OPENBANKING_TOKEN_ENCRYPTION_SECRET이 설정되지 않았습니다. " +
                    "운영(prod) 환경에서는 반드시 환경변수로 강도 높은 키를 설정해야 합니다."
            );
        }
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        // 신규 암호화는 항상 PBKDF2 키를 사용한다.
        return doEncrypt(plainText, keySpec);
    }

    public String decrypt(String encryptedText) {
        if (!StringUtils.hasText(encryptedText)) {
            return null;
        }
        // [M-7] PBKDF2 키로 먼저 시도한다. 실패하면 레거시 SHA-256 키로 fallback한다.
        // 기존 SHA-256으로 암호화된 토큰이 아직 DB에 있을 수 있으므로 fallback이 필요하다.
        try {
            return doDecrypt(encryptedText, keySpec);
        } catch (Exception ignored) {
            return doDecrypt(encryptedText, legacyKeySpec);
        }
    }

    private String doEncrypt(String plainText, SecretKeySpec key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt OpenBanking token", exception);
        }
    }

    private String doDecrypt(String encryptedText, SecretKeySpec key) {
        try {
            byte[] raw = Base64.getDecoder().decode(encryptedText);
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt OpenBanking token", exception);
        }
    }

    // [M-7] PBKDF2WithHmacSHA256로 256비트 AES 키를 유도한다.
    private byte[] pbkdf2(String password) {
        try {
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    PBKDF2_SALT,
                    PBKDF2_ITERATIONS,
                    PBKDF2_KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("PBKDF2 key derivation failed", exception);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
