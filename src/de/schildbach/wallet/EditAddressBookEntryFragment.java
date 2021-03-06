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

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public class EditAddressBookEntryFragment extends DialogFragment
{
	public static final String FRAGMENT_TAG = EditAddressBookEntryFragment.class.getName();

	private String address;

	public EditAddressBookEntryFragment(final String address)
	{
		this.address = address;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final FragmentActivity activity = getActivity();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final ContentResolver contentResolver = activity.getContentResolver();
		final Uri uri = AddressBookProvider.CONTENT_URI.buildUpon().appendPath(address).build();

		final String label;
		final Cursor cursor = contentResolver.query(uri, null, null, null, null);
		if (cursor.moveToFirst())
			label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
		else
			label = null;
		cursor.close();

		final boolean isAdd = label == null;

		final Builder dialog = new AlertDialog.Builder(activity).setTitle(isAdd ? R.string.edit_address_book_entry_dialog_title_add
				: R.string.edit_address_book_entry_dialog_title_edit);

		final View view = inflater.inflate(R.layout.edit_address_book_entry_dialog, null);

		final TextView viewAddress = (TextView) view.findViewById(R.id.edit_address_book_entry_address);
		viewAddress.setText(address);

		final TextView viewLabel = (TextView) view.findViewById(R.id.edit_address_book_entry_label);
		viewLabel.setText(label);

		dialog.setView(view);

		dialog.setPositiveButton(isAdd ? R.string.edit_address_book_entry_dialog_button_add : R.string.edit_address_book_entry_dialog_button_edit,
				new DialogInterface.OnClickListener()
				{
					public void onClick(final DialogInterface dialog, final int whichButton)
					{
						final String newLabel = viewLabel.getText().toString().trim();

						if (newLabel.length() > 0)
						{
							final ContentValues values = new ContentValues();
							values.put(AddressBookProvider.KEY_LABEL, newLabel);

							if (isAdd)
								contentResolver.insert(uri, values);
							else
								contentResolver.update(uri, values, null, null);
						}

						dismiss();
					}
				});
		if (!isAdd)
		{
			dialog.setNeutralButton(R.string.edit_address_book_entry_dialog_button_delete, new DialogInterface.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int whichButton)
				{
					contentResolver.delete(uri, null, null);
					dismiss();
				}
			});
		}
		dialog.setNegativeButton(R.string.edit_address_book_entry_dialog_button_cancel, new DialogInterface.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int whichButton)
			{
				dismiss();
			}
		});

		return dialog.create();
	}
}
