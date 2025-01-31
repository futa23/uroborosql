/**
 * Copyright (c) 2017-present, Future Corporation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package jp.co.future.uroborosql.filter;

import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.apache.commons.lang3.ArrayUtils;

/**
 * 特定のカラムの読み書きに対して暗号化/復号化を行うSQLフィルターのデフォルト実装.
 *
 * 登録、更新時はパラメータを暗号化 検索時は検索結果を復号化する
 *
 * @author H.Sugimoto
 *
 */
public class SecretColumnSqlFilter extends AbstractSecretColumnSqlFilter {

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String encrypt(final Cipher cipher, final SecretKey secretKey, final String input)
			throws GeneralSecurityException {
		byte[] crypted;
		if (isUseIV()) {
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		}
		crypted = cipher.doFinal(input.getBytes(getCharset()));
		if (isUseIV()) {
			crypted = ArrayUtils.addAll(cipher.getIV(), crypted);
		}
		return Base64.getUrlEncoder().withoutPadding().encodeToString(crypted);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String decrypt(final Cipher cipher, final SecretKey secretKey, final String secret)
			throws GeneralSecurityException {
		byte[] secretData = Base64.getUrlDecoder().decode(secret);

		if (isUseIV()) {
			int blockSize = cipher.getBlockSize();
			byte[] iv = ArrayUtils.subarray(secretData, 0, blockSize);
			secretData = ArrayUtils.subarray(secretData, blockSize, secretData.length);
			IvParameterSpec ips = new IvParameterSpec(iv);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, ips);
		}
		return new String(cipher.doFinal(secretData), getCharset());
	}
}
