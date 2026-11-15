package local.handson;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.nimbusds.openid.connect.sdk.federation.entities.EntityStatement;
import com.nimbusds.openid.connect.sdk.federation.trust.TrustChain;
import java.time.Instant;
import java.util.*;

/** An offline, direct-chain teaching profile. No discovery or client registration. */
public final class Lab {
    static final String A="https://a.example.invalid", TA="https://anchor.example.invalid", B="https://b.example.invalid";
    final RSAKey anchor=key("anchor"), other=key("other-anchor"), federation=key("a-federation"), token=key("a-token");
    public Lab() throws Exception {}
    static RSAKey key(String id) throws Exception { return new RSAKeyGenerator(2048).keyID(id).generate(); }
    static Map<String,Object> jwks(RSAKey k) { return new JWKSet(k.toPublicJWK()).toJSONObject(); }
    static SignedJWT sign(JWTClaimsSet c,RSAKey k,String type) throws Exception {
        SignedJWT j=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(type)).keyID(k.getKeyID()).build(),c);
        j.sign(new RSASSASigner(k)); return j;
    }
    static JWTClaimsSet.Builder claims(String iss,String sub,long now,long exp) {
        return new JWTClaimsSet.Builder().issuer(iss).subject(sub).issueTime(Date.from(Instant.ofEpochSecond(now))).expirationTime(Date.from(Instant.ofEpochSecond(exp)));
    }
    public Map<String,Object> run(String scenario,String trust) throws Exception {
        if(!Set.of("normal","tampered","expired","unregistered","token-key").contains(scenario) || !Set.of("anchor","other").contains(trust)) throw new IllegalArgumentException("Unknown scenario");
        long now=Instant.now().getEpochSecond();
        RSAKey aKey=scenario.equals("unregistered")?key("unregistered-a"):federation;
        var meta=Map.of("oauth_authorization_server",Map.of("issuer",A,"jwks",jwks(token)));
        SignedJWT leaf=sign(claims(A,A,now-10,scenario.equals("expired")?now-1:now+600).claim("jwks",jwks(aKey)).claim("authority_hints",List.of(TA)).claim("metadata",meta).build(),aKey,"entity-statement+jwt");
        if(scenario.equals("tampered")) {
            var changed=new LinkedHashMap<>(leaf.getJWTClaimsSet().toJSONObject()); changed.put("metadata",Map.of("oauth_authorization_server",Map.of("issuer","https://intruder.example.invalid","jwks",jwks(token))));
            var payload=com.nimbusds.jose.util.Base64URL.encode(new JWTClaimsSet.Builder(JWTClaimsSet.parse(changed)).build().toString());
            leaf=SignedJWT.parse(leaf.getHeader().toBase64URL()+"."+payload+"."+leaf.getSignature());
        }
        SignedJWT subordinate=sign(claims(TA,A,now-10,now+600).claim("jwks",jwks(federation)).build(),anchor,"entity-statement+jwt");
        SignedJWT root=sign(claims(TA,TA,now-10,now+600).claim("jwks",jwks(anchor)).claim("metadata",Map.of("federation_entity",Map.of())).build(),anchor,"entity-statement+jwt");
        SignedJWT assertion=sign(claims(A,"sample-user",now-10,now+300).audience(B).build(),scenario.equals("token-key")?other:token,"JWT");
        List<SignedJWT> chain=List.of(leaf,subordinate,root);
        var checks=new ArrayList<Map<String,Object>>(); boolean accepted=false;
        try {
            JWKSet pin=new JWKSet(trust.equals("anchor")?anchor.toPublicJWK():other.toPublicJWK());
            verify(chain,assertion,pin,checks); accepted=true;
        } catch(Exception ex) {
            checks.add(Map.of("ok",false,"label",failureLabel(checks.size())));
        }
        var docs=new ArrayList<Map<String,Object>>();
        String[] labels={"AのEntity Configuration（Aの自己署名）","AについてのSubordinate Statement（Anchorの署名）","AnchorのEntity Configuration（Anchorの自己署名）"};
        for(int i=0;i<3;i++) docs.add(Map.of("label",labels[i],"claims",chain.get(i).getJWTClaimsSet().toJSONObject(),"jwt",chain.get(i).serialize()));
        return Map.of("accepted",accepted,"scenario",scenario,"trust",trust,"checks",checks,"documents",docs,
            "assertion",Map.of("claims",assertion.getJWTClaimsSet().toJSONObject(),"jwt",assertion.serialize()),
            "keys",Map.of("pinned",(trust.equals("anchor")?anchor:other).toPublicJWK().computeThumbprint().toString(),"anchor",anchor.toPublicJWK().computeThumbprint().toString(),"federation",aKey.toPublicJWK().computeThumbprint().toString(),"token",token.toPublicJWK().computeThumbprint().toString()));
    }
    static void require(boolean v) { if(!v) throw new IllegalArgumentException("Validation failed"); }
    static void ok(List<Map<String,Object>> out,String label) { out.add(Map.of("ok",true,"label",label)); }
    static String failureLabel(int n) { return switch(n) {
        case 0 -> "文書の形式・対象・有効期間を確認できません";
        case 1 -> "Bが固定した公開鍵でAnchorを検証できません";
        case 2 -> "AnchorからAへの署名と鍵のつながりを検証できません";
        default -> "検証済みメタデータの鍵でAの提示JWTを検証できません"; }; }
    static void verify(List<SignedJWT> docs,SignedJWT assertion,JWKSet pin,List<Map<String,Object>> out) throws Exception {
        long now=Instant.now().getEpochSecond();
        for(var j:docs) {
            require(JWSAlgorithm.RS256.equals(j.getHeader().getAlgorithm()) && new JOSEObjectType("entity-statement+jwt").equals(j.getHeader().getType()));
            var c=j.getJWTClaimsSet(); require(c.getIssueTime()!=null && c.getExpirationTime()!=null && c.getIssueTime().getTime()/1000<=now && c.getExpirationTime().getTime()/1000>now && c.getExpirationTime().after(c.getIssueTime()));
        }
        var leaf=docs.get(0).getJWTClaimsSet(); var sub=docs.get(1).getJWTClaimsSet(); var root=docs.get(2).getJWTClaimsSet();
        require(A.equals(leaf.getIssuer()) && A.equals(leaf.getSubject()) && TA.equals(sub.getIssuer()) && A.equals(sub.getSubject()) && TA.equals(root.getIssuer()) && TA.equals(root.getSubject()) && List.of(TA).equals(leaf.getStringListClaim("authority_hints")));
        ok(out,"3文書の形式・対象・有効期間を確認");
        EntityStatement.parse(docs.get(2)).verifySignature(pin);
        ok(out,"Bが固定した公開鍵でAnchorの署名を検証");
        new TrustChain(EntityStatement.parse(docs.get(0)),List.of(EntityStatement.parse(docs.get(1))),EntityStatement.parse(docs.get(2))).verifySignatures(pin);
        ok(out,"Anchorが認めたAの鍵で、Aの自己署名を検証（Nimbus）");
        var metadata=leaf.getJSONObjectClaim("metadata");
        @SuppressWarnings("unchecked") var as=(Map<String,Object>)metadata.get("oauth_authorization_server");
        require(A.equals(as.get("issuer")));
        @SuppressWarnings("unchecked") var keys=JWKSet.parse((Map<String,Object>)as.get("jwks"));
        var c=assertion.getJWTClaimsSet();
        require(JWSAlgorithm.RS256.equals(assertion.getHeader().getAlgorithm()) && A.equals(c.getIssuer()) && List.of(B).equals(c.getAudience()) && c.getExpirationTime().getTime()/1000>now && c.getIssueTime().getTime()/1000<=now);
        var k=keys.getKeyByKeyId(assertion.getHeader().getKeyID());
        require(k instanceof RSAKey && assertion.verify(new RSASSAVerifier((RSAKey)k)));
        ok(out,"検証済みメタデータの鍵でAの提示JWTを検証。issuer・audience・期限も一致");
    }
}

