package com.rased.app.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme

/** Local demonstration login; no server authentication. */
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val submit = {
        if (username == "taha22" && password == "taha22") {
            focusManager.clearFocus()
            password = ""
            onLogin()
        } else {
            error = "اسم المستخدم أو كلمة المرور غير صحيحة"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F7FA)) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("راصد", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, color = Color(0xFF087F79))
                    Text("تسجيل الدخول", style = MaterialTheme.typography.headlineSmall)
                    Text("أدخل بياناتك للمتابعة", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسم المستخدم") },
                        singleLine = true,
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "إخفاء" else "إظهار")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RoundedCornerShape(12.dp)
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    Button(
                        onClick = submit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087F79))
                    ) { Text("دخول") }
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 360, heightDp = 800)
@Composable
private fun LoginScreenPreview() {
    RasedTheme { LoginScreen {} }
}
