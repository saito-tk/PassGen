package jp.passgen.core

/** Public, fictitious teaching examples. Never used for password generation or user storage.
 * Full Argon2id PHC values were computed offline; verify with scripts/verify-storage-examples.cjs.
 */
object StorageLessonExamples {
    const val PASSWORD = "Demo!7_mikan-Sora"
    const val PASSWORD_B = "Example#4_kiwi-Nami"
    const val PASSWORD_C = "Sample+9_momo-Tsuki"
    const val COMMON_SALT_HASH = "\$argon2id\$v=19\$m=19456,t=2,p=1\$63tu8G34R3Yr+91lTJVD8g\$wMwvWn18waTSBBklsCJJJekvXztJSPp8FrXRlLeGf8s"
    const val INDIVIDUAL_SALT_HASH_A = "\$argon2id\$v=19\$m=19456,t=2,p=1\$6Muh5pTVaEL9uzsCAgUVyg\$alZZ9NKLZAuwvxibNeqfv46Qj7xvkKVqS2OckUvjBv0"
    const val INDIVIDUAL_SALT_HASH_B = "\$argon2id\$v=19\$m=19456,t=2,p=1\$kdNjhmWnYyOMYQv1Mg5/ow\$h00D0/nmcs5jjp5ftPEYGs5+8PiKYRCEhgZh0y9xkWo"
}
