/*
 * Copyright 2010 the original author or authors.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;

import de.schildbach.wallet.util.ErrorReporter;
import de.schildbach.wallet.util.StrictModeWrapper;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class Application extends android.app.Application
{
	private static final int STACK_SIZE = 96 * 1024;

	private Wallet wallet;

	private final Handler handler = new Handler();

	final private WalletEventListener walletEventListener = new WalletEventListener()
	{
		@Override
		public void onPendingCoinsReceived(final Wallet wallet, final Transaction tx)
		{
			onEverything();
		}

		@Override
		public void onCoinsReceived(final Wallet w, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			onEverything();
		}

		@Override
		public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			onEverything();
		}

		@Override
		public void onReorganize()
		{
			onEverything();
		}

		@Override
		public void onDeadTransaction(final Transaction deadTx, final Transaction replacementTx)
		{
			onEverything();
		}

		private void onEverything()
		{
			handler.post(new Runnable()
			{
				public void run()
				{
					saveWallet();
				}
			});
		}
	};

	@Override
	public void onCreate()
	{
		try
		{
			StrictModeWrapper.init();
		}
		catch (final Error x)
		{
			System.out.println("StrictMode not available");
		}

		super.onCreate();

		ErrorReporter.getInstance().init(this);

		loadWallet();

		backupKeys();

		wallet.addEventListener(walletEventListener);
	}

	public NetworkParameters getNetworkParameters()
	{
		return Constants.NETWORK_PARAMETERS;
	}

	public Wallet getWallet()
	{
		return wallet;
	}

	private void loadWallet()
	{
		final String filename = Constants.TEST ? Constants.WALLET_FILENAME_TEST : Constants.WALLET_FILENAME_PROD;
		final int mode = Constants.TEST ? Constants.WALLET_MODE_TEST : Constants.WALLET_MODE_PROD;

		try
		{
			final FileInputStream is = openFileInput(filename);
			runWithStackSize(new Runnable()
			{
				public void run()
				{
					try
					{
						wallet = Wallet.loadFromFileStream(is);
					}
					catch (final EOFException x)
					{
						handleException(x);
					}
					catch (final StackOverflowError x)
					{
						handleException(x);
					}
					catch (final IOException x)
					{
						throw new Error("cannot load wallet", x);
					}
				}

				private void handleException(final Throwable x)
				{
					x.printStackTrace();

					wallet = restoreWallet();

					handler.post(new Runnable()
					{
						public void run()
						{
							Toast.makeText(Application.this, R.string.toast_wallet_reset, Toast.LENGTH_LONG).show();
						}
					});
				}
			}, STACK_SIZE);

			System.out.println("wallet loaded from: " + getFilesDir() + "/" + filename);
		}
		catch (final FileNotFoundException x)
		{
			wallet = new Wallet(Constants.NETWORK_PARAMETERS);
			wallet.keychain.add(new ECKey());

			try
			{
				wallet.saveToFileStream(openFileOutput(filename, mode));
				System.out.println("wallet created: " + getFilesDir() + "/" + filename);
			}
			catch (final IOException x2)
			{
				throw new Error("wallet cannot be created", x2);
			}
		}
	}

	public void addNewKeyToWallet()
	{
		wallet.keychain.add(new ECKey());

		saveWallet();

		backupKeys();
	}

	public void saveWallet()
	{
		final String filename = Constants.TEST ? Constants.WALLET_FILENAME_TEST : Constants.WALLET_FILENAME_PROD;
		final int mode = Constants.TEST ? Constants.WALLET_MODE_TEST : Constants.WALLET_MODE_PROD;

		runWithStackSize(new Runnable()
		{
			public void run()
			{
				try
				{
					wallet.saveToFileStream(openFileOutput(filename, mode));
					System.out.println("wallet saved to: " + getFilesDir() + "/" + filename);
				}
				catch (IOException x)
				{
					throw new RuntimeException(x);
				}
			}
		}, STACK_SIZE);
	}

	private void backupKeys()
	{
		try
		{
			final Writer out = new OutputStreamWriter(openFileOutput(Constants.WALLET_KEY_BACKUP_BASE58, Constants.WALLET_MODE), "UTF-8");

			for (final ECKey key : wallet.keychain)
			{
				out.write(key.getPrivateKeyEncoded(Constants.NETWORK_PARAMETERS).toString());
				out.write('\n');
			}

			out.close();
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}

		try
		{
			final long MS_PER_DAY = 24 * 60 * 60 * 1000;
			final String filename = String.format("%s.%02d", Constants.WALLET_KEY_BACKUP_BASE58, (System.currentTimeMillis() / MS_PER_DAY) % 100l);
			final Writer out = new OutputStreamWriter(openFileOutput(filename, Constants.WALLET_MODE), "UTF-8");

			for (final ECKey key : wallet.keychain)
			{
				out.write(key.getPrivateKeyEncoded(Constants.NETWORK_PARAMETERS).toString());
				out.write('\n');
			}

			out.close();
		}
		catch (final IOException x)
		{
			x.printStackTrace();
		}

		final ECKey firstKey = wallet.keychain.get(0);

		if (firstKey != null)
		{
			try
			{
				final byte[] asn1 = firstKey.toASN1();

				final OutputStream os = openFileOutput(Constants.WALLET_KEY_BACKUP_ASN1, Constants.WALLET_MODE);
				os.write(asn1);
				os.close();
			}
			catch (final IOException x)
			{
				x.printStackTrace();
			}
		}
	}

	private Wallet restoreWallet()
	{
		try
		{
			final Wallet wallet = new Wallet(Constants.NETWORK_PARAMETERS);
			final BufferedReader in = new BufferedReader(new InputStreamReader(openFileInput(Constants.WALLET_KEY_BACKUP_BASE58), "UTF-8"));

			while (true)
			{
				final String line = in.readLine();
				if (line == null)
					break;

				final ECKey key = new DumpedPrivateKey(Constants.NETWORK_PARAMETERS, line).getKey();
				wallet.keychain.add(key);
			}

			in.close();

			final File file = new File(getDir("blockstore", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE),
					Constants.BLOCKCHAIN_FILENAME);
			file.delete();

			return wallet;
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
		catch (final AddressFormatException x)
		{
			throw new RuntimeException(x);
		}
	}

	public Address determineSelectedAddress()
	{
		final ArrayList<ECKey> keychain = wallet.keychain;

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String defaultAddress = keychain.get(0).toAddress(Constants.NETWORK_PARAMETERS).toString();
		final String selectedAddress = prefs.getString(Constants.PREFS_KEY_SELECTED_ADDRESS, defaultAddress);

		// sanity check
		for (final ECKey key : keychain)
		{
			final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
			if (address.toString().equals(selectedAddress))
				return address;
		}

		throw new IllegalStateException("address not in keychain: " + selectedAddress);
	}

	public final int versionCode()
	{
		try
		{
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		}
		catch (NameNotFoundException x)
		{
			return 0;
		}
	}

	private static void runWithStackSize(final Runnable runnable, final long stackSize)
	{
		final Thread thread = new Thread(null, runnable, "stackSizeBooster", stackSize);
		thread.start();
		try
		{
			thread.join();
		}
		catch (final InterruptedException x)
		{
			throw new RuntimeException(x);
		}
	}
}
