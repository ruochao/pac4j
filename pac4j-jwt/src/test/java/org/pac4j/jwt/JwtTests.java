package org.pac4j.jwt;

import com.nimbusds.jose.EncryptionMethod;
import org.junit.Test;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.exception.TechnicalException;

import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.TestsConstants;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.util.TestsHelper;
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.signature.ECSignatureConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.jwt.profile.JwtProfile;
import org.pac4j.oauth.profile.facebook.FacebookAttributesDefinition;
import org.pac4j.oauth.profile.facebook.FacebookProfile;

import com.nimbusds.jose.JWSAlgorithm;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * This class tests the {@link JwtGenerator} and {@link org.pac4j.jwt.credentials.authenticator.JwtAuthenticator}.
 *
 * @author Jerome Leleu
 * @author Ruochao Zheng
 * @since 1.8.0
 */
public final class JwtTests implements TestsConstants {

    private static final String KEY2 = "02ez4f7dsq==drrdz54z---++-6ef78=";

    private static final Set<String> ROLES = new HashSet<>(Arrays.asList(new String[] { "role1", "role2"}));
    private static final Set<String> PERMISSIONS = new HashSet<>(Arrays.asList(new String[] { "perm1"}));

    @Test
    public void testGenericJwt() throws HttpAction {
        final String token =
                "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJDdXN0b20gSldUIEJ1aWxkZXIiLCJpYXQiOjE0NTAxNjQ0NTUsImV4cCI6MTQ4MTcwMDQ1NSwiYXVkIjoiaHR0cHM6Ly9naXRodWIuY29tL3BhYzRqIiwic3ViIjoidXNlckBwYWM0ai5vcmciLCJlbWFpbCI6InVzZXJAcGFjNGoub3JnIn0.zOPb7rbI3IY7iLXTK126Ggu2Q3pNCZsUzzgzgsqR7xU";

        final TokenCredentials credentials = new TokenCredentials(token, JwtAuthenticator.class.getName());
        final JwtAuthenticator authenticator = new JwtAuthenticator(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));
        authenticator.validate(credentials, null);
        assertNotNull(credentials.getUserProfile());
    }

    @Test(expected = TechnicalException.class)
    public void testGenerateAuthenticateSub() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET));
        final FacebookProfile profile = createProfile();
        profile.addAttribute(JwtClaims.SUBJECT, VALUE);
        final String token = generator.generate(profile);
        assertToken(profile, token);
    }

    @Test(expected = TechnicalException.class)
    public void testGenerateAuthenticateIat() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET));
        final FacebookProfile profile = createProfile();
        profile.addAttribute(JwtClaims.ISSUED_AT, VALUE);
        final String token = generator.generate(profile);
        assertToken(profile, token);
    }

    @Test
    public void testPlainJwt() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>();
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        assertToken(profile, token);
    }

    @Test
    public void testPemJwt() throws Exception {
        final FacebookProfile profile = createProfile();
        final ECSignatureConfiguration signatureConfiguration = buildECSignatureConfiguration();
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(signatureConfiguration);
        final String token = generator.generate(profile);
        final JwtAuthenticator authenticator = new JwtAuthenticator();
        authenticator.addSignatureConfiguration(signatureConfiguration);
        assertToken(profile, token, authenticator);
    }

    @Test
    public void testGenerateAuthenticate() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        assertToken(profile, token);
    }

    @Test
    public void testGenerateAuthenticateClaims() throws HttpAction {
        final JwtGenerator<JwtProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));
        final Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaims.SUBJECT, VALUE);
        final Date future = new Date(System.currentTimeMillis() + 1000);
        claims.put(JwtClaims.EXPIRATION_TIME, future);
        final String token = generator.generate(claims);
        final JwtAuthenticator jwtAuthenticator = new JwtAuthenticator(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));
        final JwtProfile profile = (JwtProfile) jwtAuthenticator.validateToken(token);
        assertEquals(VALUE, profile.getSubject());
        assertEquals(future.getTime() / 1000, profile.getExpirationDate().getTime() / 1000);
        final Map<String, Object> claims2 = jwtAuthenticator.validateTokenAndGetClaims(token);
        assertEquals(VALUE, claims2.get(JwtClaims.SUBJECT));
        assertEquals(future.getTime() / 1000, ((Date) claims2.get(JwtClaims.EXPIRATION_TIME)).getTime() / 1000);
    }

    @Test
    public void testGenerateAuthenticateDifferentSecrets() throws HttpAction {
        final SignatureConfiguration signatureConfiguration = new SecretSignatureConfiguration(MAC_SECRET);
        final EncryptionConfiguration encryptionConfiguration = new SecretEncryptionConfiguration(KEY2);
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(signatureConfiguration, encryptionConfiguration);
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        assertToken(profile, token, new JwtAuthenticator(signatureConfiguration, encryptionConfiguration));
    }

    @Test
    public void testGenerateAuthenticateUselessSignatureConfiguration() throws HttpAction {
        final SignatureConfiguration signatureConfiguration = new SecretSignatureConfiguration(KEY2);
        final SignatureConfiguration signatureConfiguration2 = new SecretSignatureConfiguration(MAC_SECRET);
        final EncryptionConfiguration encryptionConfiguration = new SecretEncryptionConfiguration(MAC_SECRET);
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(signatureConfiguration, encryptionConfiguration);
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        final JwtAuthenticator jwtAuthenticator = new JwtAuthenticator();
        jwtAuthenticator.addSignatureConfiguration(signatureConfiguration);
        jwtAuthenticator.addSignatureConfiguration(signatureConfiguration2);
        jwtAuthenticator.setEncryptionConfiguration(encryptionConfiguration);
        assertToken(profile, token, jwtAuthenticator);
    }

    @Test
    public void testGenerateAuthenticateSlightlyDifferentSignatureConfiguration() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(KEY2));
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        final JwtAuthenticator jwtAuthenticator = new JwtAuthenticator();
        jwtAuthenticator.addSignatureConfiguration(new SecretSignatureConfiguration(MAC_SECRET));
        final Exception e = TestsHelper.expectException(() -> assertToken(profile, token, jwtAuthenticator));
        assertTrue(e.getMessage().startsWith("JWT verification failed"));
    }

    @Test
    public void testGenerateAuthenticateDifferentSignatureConfiguration() throws Exception {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(KEY2));
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        final JwtAuthenticator jwtAuthenticator = new JwtAuthenticator();
        jwtAuthenticator.addSignatureConfiguration(buildECSignatureConfiguration());
        final Exception e = TestsHelper.expectException(() -> assertToken(profile, token, jwtAuthenticator));
        assertTrue(e.getMessage().startsWith("No signature algorithm found for JWT:"));
    }

    @Test
    public void testGenerateAuthenticateDifferentEncryptionConfiguration() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>();
        generator.setEncryptionConfiguration(new SecretEncryptionConfiguration(KEY2));
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        final JwtAuthenticator jwtAuthenticator = new JwtAuthenticator();
        jwtAuthenticator.addEncryptionConfiguration(new SecretEncryptionConfiguration(MAC_SECRET));
        final Exception e = TestsHelper.expectException(() -> assertToken(profile, token, jwtAuthenticator));
        assertTrue(e.getMessage().startsWith("No encryption algorithm found for JWT:"));
    }

    @Test
    public void testGenerateAuthenticateNotEncrypted() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET));
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        assertToken(profile, token);
    }

    @Test
    public void testGenerateAuthenticateNotSigned() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>();
        generator.setEncryptionConfiguration(new SecretEncryptionConfiguration(MAC_SECRET));
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        assertToken(profile, token);
    }

    @Deprecated
    @Test
    public void testGenerateAuthenticateAndEncrypted() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(MAC_SECRET, MAC_SECRET);
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        assertToken(profile, token, new JwtAuthenticator(MAC_SECRET, MAC_SECRET));
    }

    @Test
    public void testGenerateAuthenticateAndEncryptedWithRolesPermissions() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET));
        final FacebookProfile profile = createProfile();
        profile.addRoles(ROLES);
        profile.addPermissions(PERMISSIONS);
        final String token = generator.generate(profile);
        final CommonProfile profile2 = assertToken(profile, token);
        assertEquals(ROLES, profile2.getRoles());
        assertEquals(PERMISSIONS, profile2.getPermissions());
    }

    @Deprecated
    @Test
    public void testGenerateAuthenticateAndEncryptedDifferentKeys() throws HttpAction {
        final JwtGenerator<FacebookProfile> generator = new JwtGenerator<>(MAC_SECRET, KEY2);
        final FacebookProfile profile = createProfile();
        final String token = generator.generate(profile);
        assertToken(profile, token, new JwtAuthenticator(MAC_SECRET, KEY2));
    }

    private CommonProfile assertToken(FacebookProfile profile, String token) throws HttpAction {
        return assertToken(profile, token, new JwtAuthenticator(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET)));
    }

    private CommonProfile assertToken(FacebookProfile profile, String token, JwtAuthenticator authenticator) throws HttpAction {
        final TokenCredentials credentials = new TokenCredentials(token, CLIENT_NAME);
        authenticator.validate(credentials, null);
        final CommonProfile profile2 = credentials.getUserProfile();
        assertTrue(profile2 instanceof FacebookProfile);
        final FacebookProfile fbProfile = (FacebookProfile) profile2;
        assertEquals(profile.getTypedId(), fbProfile.getTypedId());
        assertEquals(profile.getFirstName(), fbProfile.getFirstName());
        assertEquals(profile.getDisplayName(), fbProfile.getDisplayName());
        assertEquals(profile.getFamilyName(), fbProfile.getFamilyName());
        assertEquals(profile.getVerified(), fbProfile.getVerified());
        return profile2;
    }

    private FacebookProfile createProfile() {
        final FacebookProfile profile = new FacebookProfile();
        profile.setId(ID);
        profile.addAttribute(FacebookAttributesDefinition.NAME, NAME);
        profile.addAttribute(FacebookAttributesDefinition.VERIFIED, true);
        return profile;
    }

    @Test(expected = TechnicalException.class)
    public void testAuthenticateFailed() throws HttpAction {
        final JwtAuthenticator authenticator = new JwtAuthenticator(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));
        final TokenCredentials credentials = new TokenCredentials("fakeToken", CLIENT_NAME);
        authenticator.validate(credentials, null);
    }
    
    @Test
    public void testJwtGenerationA256CBC() {
        final JwtGenerator<CommonProfile> g = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET),
                new SecretEncryptionConfiguration(KEY2 + KEY2)
        );
        ((SecretEncryptionConfiguration) g.getEncryptionConfiguration()).setMethod(EncryptionMethod.A256CBC_HS512);
        final String g1 = g.generate(new CommonProfile());
        assertNotNull(g1);
    }

    @Test
    public void testJwtGenerationA256GCM() {
        final JwtGenerator<CommonProfile> g = new JwtGenerator<>(
                new SecretSignatureConfiguration(MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET + MAC_SECRET),
                new SecretEncryptionConfiguration(MAC_SECRET)
        );
        ((SecretEncryptionConfiguration) g.getEncryptionConfiguration()).setMethod(EncryptionMethod.A256GCM);
        final String g1 = g.generate(new CommonProfile());
        assertNotNull(g1);
    }

    private ECSignatureConfiguration buildECSignatureConfiguration() throws NoSuchAlgorithmException {
        final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        final KeyPair keyPair = keyGen.generateKeyPair();
        return new ECSignatureConfiguration(keyPair, JWSAlgorithm.ES256);
    }
    
    @Test
    public void testJwtValidationExpired() {
        final JwtGenerator<JwtProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));
        final Map<String, Object> claims = new HashMap<>();        
        final Date past = new Date(System.currentTimeMillis() - 1);
        claims.put(JwtClaims.SUBJECT, VALUE);
        claims.put(JwtClaims.EXPIRATION_TIME, past);
        final String token = generator.generate(claims);
        final JwtAuthenticator jwtAuthenticator = new JwtAuthenticator(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));       
        try {
            jwtAuthenticator.validateToken(token);
            fail();
        } catch(CredentialsException e) {       
            assertTrue(e.getMessage().startsWith("Token expired: exp="));
        }       
    }
    
    @Test
    public void testJwtValidationNotExpired() {
        final JwtGenerator<JwtProfile> generator = new JwtGenerator<>(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));
        final Map<String, Object> claims = new HashMap<>();        
        final Date future = new Date(System.currentTimeMillis() + 10000);
        claims.put(JwtClaims.SUBJECT, VALUE);
        claims.put(JwtClaims.EXPIRATION_TIME, future);
        final String token = generator.generate(claims);
        final JwtAuthenticator jwtAuthenticator = new JwtAuthenticator(new SecretSignatureConfiguration(MAC_SECRET), new SecretEncryptionConfiguration(MAC_SECRET));       
        try {
            jwtAuthenticator.validateToken(token);       
        } catch(CredentialsException e) {       
            fail();
        }       
    }
    
}
