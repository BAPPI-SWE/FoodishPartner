package com.yumzy.partner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.partner.auth.AuthScreen
import com.yumzy.partner.auth.AuthViewModel
import com.yumzy.partner.auth.EmailAuthClient
import com.yumzy.partner.auth.EmailAuthScreen
import com.yumzy.partner.auth.GoogleAuthUiClient
import com.yumzy.partner.features.dashboard.PartnerDashboardScreen
import com.yumzy.partner.features.menu.AddMenuItemScreen
import com.yumzy.partner.features.menu.CategoryDetailScreen
import com.yumzy.partner.features.menu.CreateCategoryScreen
import com.yumzy.partner.features.menu.EditCategoryScreen
import com.yumzy.partner.features.menu.EditMenuItemScreen
import com.yumzy.partner.features.orders.Order
import com.yumzy.partner.features.orders.OrderListScreen
import com.yumzy.partner.features.profile.EditProfileScreen
import com.yumzy.partner.features.profile.RestaurantProfileScreen
import com.yumzy.partner.notifications.OneSignalNotificationHelper
import com.yumzy.partner.ui.theme.YumzyPartnerTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    private val emailAuthClient by lazy {
        EmailAuthClient()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YumzyPartnerTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "auth") {

                    composable("auth") {
                        val viewModel = viewModel<AuthViewModel>()
                        val state by viewModel.state.collectAsStateWithLifecycle()

                        LaunchedEffect(Unit) {
                            val currentUser = googleAuthUiClient.getSignedInUser()
                                ?: emailAuthClient.getSignedInUser()
                            if (currentUser != null) {
                                checkRestaurantProfile(currentUser.userId, navController)
                            }
                        }

                        val launcher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartIntentSenderForResult()
                        ) { result ->
                            if (result.resultCode == RESULT_OK) {
                                lifecycleScope.launch {
                                    val signInResult = googleAuthUiClient.signInWithIntent(
                                        intent = result.data ?: return@launch
                                    )
                                    viewModel.onSignInResult(signInResult)
                                }
                            }
                        }

                        LaunchedEffect(state.isSignInSuccessful) {
                            if (state.isSignInSuccessful) {
                                val userId = googleAuthUiClient.getSignedInUser()?.userId
                                    ?: emailAuthClient.getSignedInUser()?.userId
                                if (userId != null) checkRestaurantProfile(userId, navController)
                                viewModel.resetState()
                            }
                        }

                        AuthScreen(
                            onSignInSuccess = {
                                lifecycleScope.launch {
                                    val signInIntentSender = googleAuthUiClient.signIn()
                                    launcher.launch(
                                        IntentSenderRequest.Builder(
                                            signInIntentSender ?: return@launch
                                        ).build()
                                    )
                                }
                            },
                            onEmailSignIn = {
                                navController.navigate("email_auth")
                            }
                        )
                    }

                    composable("email_auth") {
                        val viewModel = viewModel<AuthViewModel>()
                        var isLoading by remember { mutableStateOf(false) }

                        EmailAuthScreen(
                            onBackClicked = { navController.popBackStack() },
                            onSignInSuccess = {
                                val userId = emailAuthClient.getSignedInUser()?.userId
                                if (userId != null) {
                                    checkRestaurantProfile(userId, navController)
                                }
                            },
                            onSignIn = { email, password ->
                                isLoading = true
                                lifecycleScope.launch {
                                    val result = emailAuthClient.signInWithEmail(email, password)
                                    isLoading = false

                                    if (result.data != null) {
                                        viewModel.onSignInResult(result)
                                        val userId = result.data.userId
                                        checkRestaurantProfile(userId, navController)
                                    } else {
                                        Toast.makeText(
                                            applicationContext,
                                            result.errorMessage ?: "Sign in failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            onSignUp = { email, password, name ->
                                isLoading = true
                                lifecycleScope.launch {
                                    val result = emailAuthClient.signUpWithEmail(email, password, name)
                                    isLoading = false

                                    if (result.data != null) {
                                        Toast.makeText(
                                            applicationContext,
                                            "Account created successfully!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        viewModel.onSignInResult(result)
                                        val userId = result.data.userId
                                        checkRestaurantProfile(userId, navController)
                                    } else {
                                        Toast.makeText(
                                            applicationContext,
                                            result.errorMessage ?: "Sign up failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            onForgotPassword = { email ->
                                lifecycleScope.launch {
                                    val result = emailAuthClient.sendPasswordResetEmail(email)
                                    if (result.success) {
                                        Toast.makeText(
                                            applicationContext,
                                            "Password reset email sent! Check your inbox.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            applicationContext,
                                            result.errorMessage ?: "Failed to send reset email",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            isLoading = isLoading
                        )
                    }

                    composable("create_profile") {
                        val userId = Firebase.auth.currentUser?.uid ?: return@composable
                        RestaurantProfileScreen(onSaveClicked = { name, cuisine, deliveryLocations ->
                            val restaurantProfile = hashMapOf(
                                "ownerId" to userId,
                                "name" to name,
                                "cuisine" to cuisine,
                                "deliveryLocations" to deliveryLocations,
                                "email" to (Firebase.auth.currentUser?.email ?: "")
                            )

                            Firebase.firestore.collection("restaurants").document(userId)
                                .set(restaurantProfile)
                                .addOnSuccessListener {
                                    navController.navigate("dashboard") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                        })
                    }

                    composable("dashboard") {
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        PartnerDashboardScreen(
                            onNavigateToCreateCategory = { navController.navigate("create_category") },
                            onNavigateToAddItem = { category ->
                                val encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
                                navController.navigate("add_item/$encodedCategory")
                            },
                            onNavigateToCategoryDetail = { categoryName ->
                                val encodedCategoryName = URLEncoder.encode(categoryName, StandardCharsets.UTF_8.toString())
                                navController.navigate("category_detail/$encodedCategoryName")
                            },
                            onNavigateToEditProfile = { navController.navigate("edit_profile") },
                            onDeleteItem = { itemId -> deleteMenuItem(ownerId, itemId) },
                            onDeleteCategory = { category ->
                                deleteCategory(ownerId, category.id, "Pre-order ${category.name}")
                            },
                            onEditCategory = { category ->
                                val encodedId = URLEncoder.encode(category.id, StandardCharsets.UTF_8.toString())
                                navController.navigate("edit_category/$encodedId")
                            },
                            onEditItem = { item ->
                                val encodedId = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString())
                                val encodedCategory = URLEncoder.encode(item.category, StandardCharsets.UTF_8.toString())
                                navController.navigate("edit_item/$encodedId/$encodedCategory")
                            }
                        )
                    }

                    composable("edit_profile") {
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        EditProfileScreen(
                            onSaveChanges = { name, cuisine, imageUrl, deliveryLocations ->
                                val updates = mapOf(
                                    "name" to name,
                                    "cuisine" to cuisine,
                                    "imageUrl" to imageUrl,
                                    "deliveryLocations" to deliveryLocations
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .update(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "Profile Updated", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                            },
                            onLogout = {
                                lifecycleScope.launch {
                                    try {
                                        googleAuthUiClient.signOut()
                                        emailAuthClient.signOut()
                                        navController.navigate("auth") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                        Toast.makeText(applicationContext, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(applicationContext, "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }

                    composable("create_category") {
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        CreateCategoryScreen(
                            onSaveCategory = { categoryName, startTime, endTime, deliveryTime ->
                                val categoryData = hashMapOf(
                                    "name" to categoryName,
                                    "startTime" to startTime,
                                    "endTime" to endTime,
                                    "deliveryTime" to deliveryTime
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .collection("preOrderCategories")
                                    .add(categoryData)
                                    .addOnSuccessListener { navController.popBackStack() }
                            }
                        )
                    }

                    composable(
                        "edit_category/{categoryId}",
                        arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        val encodedCategoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
                        val categoryId = URLDecoder.decode(encodedCategoryId, StandardCharsets.UTF_8.toString())

                        EditCategoryScreen(
                            categoryId = categoryId,
                            onSaveCategory = { categoryName, startTime, endTime, deliveryTime ->
                                val categoryData = mapOf(
                                    "name" to categoryName,
                                    "startTime" to startTime,
                                    "endTime" to endTime,
                                    "deliveryTime" to deliveryTime
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .collection("preOrderCategories")
                                    .document(categoryId)
                                    .update(categoryData)
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "Category Updated", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                            }
                        )
                    }

                    composable(
                        "category_detail/{categoryName}",
                        arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        val encodedCategoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                        val categoryName = URLDecoder.decode(encodedCategoryName, StandardCharsets.UTF_8.toString())

                        CategoryDetailScreen(
                            categoryName = categoryName,
                            onNavigateToAddItem = { category ->
                                val encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
                                navController.navigate("add_item/$encodedCategory")
                            },
                            onNavigateToOrderList = { category ->
                                val encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
                                navController.navigate("order_list/$encodedCategory")
                            },
                            onDeleteItem = { itemId -> deleteMenuItem(ownerId, itemId) },
                            onEditItem = { item ->
                                val encodedId = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString())
                                val encodedCategory = URLEncoder.encode(item.category, StandardCharsets.UTF_8.toString())
                                navController.navigate("edit_item/$encodedId/$encodedCategory")
                            }
                        )
                    }

                    composable(
                        "order_list/{categoryName}",
                        arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedCategoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                        val categoryName = URLDecoder.decode(encodedCategoryName, StandardCharsets.UTF_8.toString())

                        OrderListScreen(
                            categoryName = categoryName,
                            onBackClicked = { navController.popBackStack() },
                            onAcceptOrder = { orderId, userId ->
                                updateOrderStatus(orderId, userId, "Accepted")
                            },
                            onRejectOrder = { orderId, userId ->
                                updateOrderStatus(orderId, userId, "Rejected")
                            },
                            onDeleteOrder = { orderId ->
                                deleteOrder(orderId)
                            },
                            onMarkAsPaid = { orderId ->
                                markOrderAsPaid(orderId)
                            },
                            onAcceptAllOrders = { orders ->
                                updateAllOrdersStatus(orders, "Accepted")
                            },
                            onRejectAllOrders = { orders ->
                                updateAllOrdersStatus(orders, "Rejected")
                            },
                            onDeleteAllOrders = { orders ->
                                deleteAllOrders(orders)
                            },
                            onSendCustomNotification = { orderIds, message ->
                                sendCustomNotifications(orderIds, message)
                            }
                        )
                    }

                    composable(
                        "add_item/{category}",
                        arguments = listOf(navArgument("category") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        val encodedCategory = backStackEntry.arguments?.getString("category") ?: "Current Menu"
                        val category = URLDecoder.decode(encodedCategory, StandardCharsets.UTF_8.toString())

                        AddMenuItemScreen(
                            category = category,
                            onSaveItemClicked = { itemName, price ->
                                val newItem = hashMapOf(
                                    "name" to itemName,
                                    "price" to (price.toDoubleOrNull() ?: 0.0),
                                    "category" to category
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .collection("menuItems")
                                    .add(newItem)
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "$itemName added!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                            }
                        )
                    }

                    composable(
                        "edit_item/{itemId}/{category}",
                        arguments = listOf(
                            navArgument("itemId") { type = NavType.StringType },
                            navArgument("category") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val ownerId = Firebase.auth.currentUser?.uid ?: return@composable
                        val encodedItemId = backStackEntry.arguments?.getString("itemId") ?: ""
                        val itemId = URLDecoder.decode(encodedItemId, StandardCharsets.UTF_8.toString())
                        val encodedCategory = backStackEntry.arguments?.getString("category") ?: ""
                        val category = URLDecoder.decode(encodedCategory, StandardCharsets.UTF_8.toString())

                        EditMenuItemScreen(
                            itemId = itemId,
                            category = category,
                            onSaveItemClicked = { itemName, price ->
                                val updates = mapOf(
                                    "name" to itemName,
                                    "price" to (price.toDoubleOrNull() ?: 0.0)
                                )
                                Firebase.firestore.collection("restaurants").document(ownerId)
                                    .collection("menuItems")
                                    .document(itemId)
                                    .update(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "Item updated!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkRestaurantProfile(userId: String, navController: NavController) {
        val db = Firebase.firestore
        db.collection("restaurants").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    navController.navigate("dashboard") {
                        popUpTo("auth") { inclusive = true }
                    }
                } else {
                    navController.navigate("create_profile") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            }
    }

    private fun deleteMenuItem(ownerId: String, itemId: String) {
        Firebase.firestore.collection("restaurants").document(ownerId)
            .collection("menuItems").document(itemId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Item deleted", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteCategory(ownerId: String, categoryId: String, categoryName: String) {
        val db = Firebase.firestore
        val restaurantRef = db.collection("restaurants").document(ownerId)
        restaurantRef.collection("menuItems").whereEqualTo("category", categoryName).get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (document in snapshot.documents) batch.delete(document.reference)
                batch.commit().addOnSuccessListener {
                    restaurantRef.collection("preOrderCategories").document(categoryId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(applicationContext, "Category deleted", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }

    private fun deleteOrder(orderId: String) {
        lifecycleScope.launch {
            try {
                Firebase.firestore.collection("orders").document(orderId)
                    .delete()
                    .await()
                Toast.makeText(applicationContext, "Order deleted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error deleting order: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markOrderAsPaid(orderId: String) {
        lifecycleScope.launch {
            try {
                Firebase.firestore.collection("orders").document(orderId)
                    .update("isPaid", true)
                    .await()
                Toast.makeText(applicationContext, "Order marked as paid", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error marking as paid: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOrderStatus(orderId: String, userId: String, newStatus: String) {
        lifecycleScope.launch {
            try {
                val db = Firebase.firestore
                val restaurantId = Firebase.auth.currentUser?.uid ?: return@launch
                val restaurantDoc = db.collection("restaurants").document(restaurantId).get().await()
                val restaurantName = restaurantDoc.getString("name") ?: "Your Restaurant"

                if (newStatus == "Rejected") {
                    db.collection("orders").document(orderId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(applicationContext, "Order rejected and removed", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    db.collection("orders").document(orderId)
                        .update("orderStatus", newStatus)
                        .addOnSuccessListener {
                            Toast.makeText(applicationContext, "Order marked as $newStatus", Toast.LENGTH_SHORT).show()
                        }
                }

                OneSignalNotificationHelper.sendOrderStatusNotification(
                    userId = userId,
                    orderId = orderId,
                    newStatus = newStatus,
                    restaurantName = restaurantName
                )

            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAllOrdersStatus(orders: List<Order>, newStatus: String) {
        if (orders.isEmpty()) return
        lifecycleScope.launch {
            try {
                val db = Firebase.firestore
                val restaurantId = Firebase.auth.currentUser?.uid ?: return@launch
                val restaurantDoc = db.collection("restaurants").document(restaurantId).get().await()
                val restaurantName = restaurantDoc.getString("name") ?: "Your Restaurant"

                val batch = db.batch()
                orders.forEach { order ->
                    val docRef = db.collection("orders").document(order.id)
                    if (newStatus == "Rejected") {
                        batch.delete(docRef)
                    } else {
                        batch.update(docRef, "orderStatus", newStatus)
                    }
                }
                batch.commit().await()

                val message = if (newStatus == "Rejected") {
                    "${orders.size} orders rejected and removed"
                } else {
                    "${orders.size} orders marked as $newStatus"
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

                OneSignalNotificationHelper.sendBulkOrderStatusNotification(
                    orderIds = orders.map { it.id },
                    newStatus = newStatus,
                    restaurantName = restaurantName
                )

            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error updating orders: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteAllOrders(orders: List<Order>) {
        if (orders.isEmpty()) return
        lifecycleScope.launch {
            try {
                val db = Firebase.firestore
                val batch = db.batch()
                orders.forEach { order ->
                    val docRef = db.collection("orders").document(order.id)
                    batch.delete(docRef)
                }
                batch.commit().await()

                Toast.makeText(applicationContext, "${orders.size} orders deleted successfully", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error deleting orders: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendCustomNotifications(orderIds: List<String>, message: String) {
        lifecycleScope.launch {
            try {
                val restaurantId = Firebase.auth.currentUser?.uid ?: return@launch
                val restaurantDoc = Firebase.firestore.collection("restaurants").document(restaurantId).get().await()
                val restaurantName = restaurantDoc.getString("name") ?: "Your Restaurant"

                val success = OneSignalNotificationHelper.sendCustomNotificationToUsers(
                    orderIds, message, restaurantName
                )

                if (success) Toast.makeText(applicationContext, "Notification sent!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(applicationContext, "No users found or failed to send", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error sending notification: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}