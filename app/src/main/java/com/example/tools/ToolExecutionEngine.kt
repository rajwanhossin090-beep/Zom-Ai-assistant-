package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles deep device control and function calling tools for Gemini AI assistant (MJ).
 */
class ToolExecutionEngine(private val context: Context) {

    /**
     * Get Gemini Function Declarations JSON list for tools.
     */
    fun getToolDeclarationsJson(): JSONArray {
        val toolsArray = JSONArray()

        // 1. openApp
        val openApp = JSONObject().apply {
            put("name", "openApp")
            put("description", "Opens an app on the device by app name or package name (e.g. YouTube, Instagram, WhatsApp, Calculator, Camera).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("packageName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the app or package to open (e.g., 'YouTube', 'Instagram', 'com.google.android.youtube')")
                    })
                })
                put("required", JSONArray().apply { put("packageName") })
            })
        }

        // 2. searchAndCallContact
        val searchAndCallContact = JSONObject().apply {
            put("name", "searchAndCallContact")
            put("description", "Searches the user's contacts by name and initiates a phone call.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The contact name to search for and call.")
                    })
                })
                put("required", JSONArray().apply { put("contactName") })
            })
        }

        // 3. sendWhatsAppMessage
        val sendWhatsAppMessage = JSONObject().apply {
            put("name", "sendWhatsAppMessage")
            put("description", "Searches for a contact's phone number and deep-links into WhatsApp to send a pre-filled text message.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the contact to message.")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The text message content to send.")
                    })
                })
                put("required", JSONArray().apply {
                    put("contactName")
                    put("message")
                })
            })
        }

        // 4. sendGmail
        val sendGmail = JSONObject().apply {
            put("name", "sendGmail")
            put("description", "Opens an email client to send an email to a recipient with subject and body.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("recipientEmail", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The recipient's email address.")
                    })
                    put("subject", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Email subject line.")
                    })
                    put("body", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Email body content.")
                    })
                })
                put("required", JSONArray().apply {
                    put("recipientEmail")
                    put("subject")
                    put("body")
                })
            })
        }

        toolsArray.put(openApp)
        toolsArray.put(searchAndCallContact)
        toolsArray.put(sendWhatsAppMessage)
        toolsArray.put(sendGmail)

        return toolsArray
    }

    /**
     * Executes a tool call from Gemini and returns the execution result string.
     */
    fun executeTool(functionName: String, args: JSONObject): String {
        return try {
            when (functionName) {
                "openApp" -> {
                    val appNameOrPackage = args.optString("packageName", "")
                    executeOpenApp(appNameOrPackage)
                }
                "searchAndCallContact" -> {
                    val contactName = args.optString("contactName", "")
                    executeSearchAndCallContact(contactName)
                }
                "sendWhatsAppMessage" -> {
                    val contactName = args.optString("contactName", "")
                    val message = args.optString("message", "")
                    executeSendWhatsAppMessage(contactName, message)
                }
                "sendGmail" -> {
                    val email = args.optString("recipientEmail", "")
                    val subject = args.optString("subject", "")
                    val body = args.optString("body", "")
                    executeSendGmail(email, subject, body)
                }
                else -> "ERROR: Unknown function $functionName"
            }
        } catch (e: Exception) {
            "ERROR executing $functionName: ${e.message}"
        }
    }

    private fun executeOpenApp(appNameOrPackage: String): String {
        val pm = context.packageManager

        // Try direct launch intent
        val directIntent = pm.getLaunchIntentForPackage(appNameOrPackage)
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return "SUCCESS: Opened package $appNameOrPackage"
        }

        // Search by app label
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val queryLower = appNameOrPackage.lowercase().trim()

        for (appInfo in installedApps) {
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            if (label.contains(queryLower) || appInfo.packageName.lowercase().contains(queryLower)) {
                val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "SUCCESS: Opened app '${pm.getApplicationLabel(appInfo)}' (${appInfo.packageName})"
                }
            }
        }

        // Fallback: search in Google Play or store
        return "FAILED: App matching '$appNameOrPackage' was not found on device. Ask the user if they'd like to install it."
    }

    private fun executeSearchAndCallContact(contactName: String): String {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return "PERMISSION_DENIED: READ_CONTACTS permission is required to search contacts. Sassily ask the user to grant contact permissions in settings!"
        }
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            return "PERMISSION_DENIED: CALL_PHONE permission is required to make phone calls. Sassily tell the user you can't place calls for them until they turn on Phone permission!"
        }

        val phoneNumber = findContactPhoneNumber(contactName)
            ?: return "FAILED: Could not find phone number for contact '$contactName'. Ask the user to check the contact name or add a phone number."

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "SUCCESS: Placed call to $contactName at $phoneNumber"
    }

    private fun executeSendWhatsAppMessage(contactName: String, message: String): String {
        var phoneNumber: String? = null

        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            phoneNumber = findContactPhoneNumber(contactName)
        }

        val cleanPhone = phoneNumber?.replace("[^0-9+]".toRegex(), "") ?: ""
        val encodedMsg = Uri.encode(message)

        val intent = if (cleanPhone.isNotEmpty()) {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            if (cleanPhone.isNotEmpty()) {
                "SUCCESS: Opened WhatsApp with pre-filled message for $contactName ($cleanPhone)"
            } else {
                "SUCCESS: Opened WhatsApp with pre-filled message for $contactName"
            }
        } catch (e: Exception) {
            // Fallback to general share intent if WhatsApp is not directly package-matched
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("whatsapp://send?text=$encodedMsg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(genericIntent)
                "SUCCESS: Launched WhatsApp link for $contactName"
            } catch (ex: Exception) {
                "FAILED: WhatsApp is not installed on this device."
            }
        }
    }

    private fun executeSendGmail(email: String, subject: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            "SUCCESS: Opened email composer for recipient $email"
        } catch (e: Exception) {
            "FAILED: No email application found to handle mailto intent."
        }
    }

    private fun findContactPhoneNumber(contactName: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$contactName%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex >= 0) {
                    return it.getString(numberIndex)
                }
            }
        }
        return null
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
