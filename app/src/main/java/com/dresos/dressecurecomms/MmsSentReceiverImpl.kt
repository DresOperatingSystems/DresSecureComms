/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import com.klinker.android.send_message.MmsSentReceiver

/**
 * Required by android-smsmms: the library broadcasts to a child of MmsSentReceiver when an MMS
 * finishes sending. The base class marks the message as sent in the provider; we need no extra work.
 */
class MmsSentReceiverImpl : MmsSentReceiver()
