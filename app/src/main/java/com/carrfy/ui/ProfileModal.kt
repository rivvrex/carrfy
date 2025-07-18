package com.carrfy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ProfileModal(
    userName: String,
    userEmail: String,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onImportPlaylist: (String, (String) -> Unit) -> Unit  // Takes URL and error callback
) {
    var playlistUrl by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf("") }
    var importSuccess by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var showImportField by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2A2A))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profile Header
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFF9C27B0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.firstOrNull()?.uppercase() ?: "U",
                        fontSize = 28.sp,
                        color = Color.White
                    )
                }

                Text(
                    text = userName,
                    fontSize = 20.sp,
                    color = Color.White
                )

                Text(
                    text = userEmail,
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Spacer(Modifier.height(8.dp))

                // Error Message (Red)
                if (importError.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF5F0000), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = importError,
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }

                // Success Message (Green)
                if (importSuccess.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF005F00), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = importSuccess,
                            fontSize = 12.sp,
                            color = Color(0xFF6BFF6B)
                        )
                    }
                }

                // Import Playlist Section
                if (!showImportField) {
                    Button(
                        onClick = { 
                            showImportField = true
                            importError = ""
                            importSuccess = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0)
                        ),
                        enabled = !isImporting
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Import",
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp)
                        )
                        Text("Import Playlist", fontSize = 14.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3A3A3A), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Paste playlist URL or ID:", fontSize = 12.sp, color = Color.White)
                        
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = { 
                                playlistUrl = it
                                importError = ""
                            },
                            placeholder = { Text("https://open.spotify.com/playlist/...", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF9C27B0),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 11.sp),
                            enabled = !isImporting
                        )

                        // Loading indicator
                        if (isImporting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = Color(0xFF9C27B0),
                                        strokeWidth = 3.dp
                                    )
                                    Text(
                                        "Importing playlist...",
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (playlistUrl.isNotBlank()) {
                                        isImporting = true
                                        onImportPlaylist(playlistUrl) { error ->
                                            isImporting = false
                                            if (error.isEmpty()) {
                                                importSuccess = "Playlist imported successfully!"
                                                playlistUrl = ""
                                                showImportField = false
                                            } else {
                                                importError = error
                                            }
                                        }
                                    } else {
                                        importError = "Please enter a URL or playlist ID"
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF9C27B0)
                                ),
                                enabled = !isImporting
                            ) {
                                Text("Import", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    showImportField = false
                                    playlistUrl = ""
                                    importError = ""
                                    importSuccess = ""
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF404040)
                                ),
                                enabled = !isImporting
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Logout Button
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    ),
                    enabled = !isImporting
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Logout",
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp)
                    )
                    Text("Logout", fontSize = 14.sp)
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF404040)
                    ),
                    enabled = !isImporting
                ) {
                    Text("Close", fontSize = 14.sp)
                }
            }
        }
    }
}
