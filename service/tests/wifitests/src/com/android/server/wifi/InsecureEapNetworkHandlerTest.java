/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.test.TestAlarmManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.Principal;
import java.security.cert.X509Certificate;

/**
 * Unit tests for {@link com.android.server.wifi.InsecureEapNetworkHandlerTest}.
 */
@SmallTest
public class InsecureEapNetworkHandlerTest extends WifiBaseTest {

    private static final int ACTION_ACCEPT = 0;
    private static final int ACTION_REJECT = 1;
    private static final int ACTION_TAP = 2;
    private static final String WIFI_IFACE_NAME = "wlan-test-9";
    private static final int FRAMEWORK_NETWORK_ID = 2;
    private static final String TEST_SSID = "\"test_ssid\"";

    @Mock WifiContext mContext;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiNative mWifiNative;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock InsecureEapNetworkHandler.InsecureEapNetworkHandlerCallbacks mCallbacks;
    @Mock Clock mClock;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Notification.Builder mNotificationBuilder;
    @Mock private WifiDialogManager.DialogHandle mTofuAlertDialog;

    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;

    TestLooper mLooper;
    Handler mHandler;
    TestAlarmManager mTestAlarmManager;
    MockResources mResources;
    InsecureEapNetworkHandler mInsecureEapNetworkHandler;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        when(mContext.getString(anyInt())).thenReturn("TestString");
        when(mContext.getString(anyInt(), any())).thenReturn("TestStringWithArgument");
        when(mContext.getText(anyInt())).thenReturn("TestStr");
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiDialogManager.createLegacySimpleDialogWithUrl(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(mTofuAlertDialog);
        when(mWifiDialogManager.createLegacySimpleDialog(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mTofuAlertDialog);

        when(mFrameworkFacade.makeNotificationBuilder(any(), any()))
                .thenReturn(mNotificationBuilder);

        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        mTestAlarmManager = new TestAlarmManager();
    }

    @After
    public void cleanUp() throws Exception {
        validateMockitoUsage();
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Tap the notification to show the dialog.
     */
    @Test
    public void verifyTrustOnFirstUseTapWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_TAP,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify that it reports errors if there is no pending Root CA certifiate
     * with Trust On First Use support.
     */
    @Test
    public void verifyTrustOnFirstUseWhenTrustOnFirstUseNoPendingCert() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        assertTrue(mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that Trust On First Use is not supported on T.
     * It follows the same behavior on preT release.
     */
    @Test
    public void verifyTrustOnFirstUseWhenTrustOnFirstUseNotSupported() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = false, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        assertTrue(mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks, never()).onError(any());
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - Trust On First Use is not supported.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyLegacyEapNetworkRejectWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is auto-connected.
     * - Accept the connection.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - Trust On First Use is not supported.
     * - This network is auto-connected.
     * - Reject the connection.
     */
    @Test
    public void verifyLegacyEapNetworkRejectWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is selected by a user.
     * - Tap the notification
     */
    @Test
    public void verifyLegacyEapNetworkOpenLinkWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_TAP,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    private X509Certificate generateMockCert(String subject, String issuer, boolean isCa) {
        X509Certificate mockCert = mock(X509Certificate.class);
        Principal mockSubjectDn = mock(Principal.class);
        when(mockCert.getSubjectDN()).thenReturn(mockSubjectDn);
        when(mockSubjectDn.getName()).thenReturn(
                "C=TW,ST=Taiwan,L=Taipei,O=Google,CN=" + subject);

        Principal mockIssuerDn = mock(Principal.class);
        when(mockCert.getIssuerDN()).thenReturn(mockIssuerDn);
        when(mockIssuerDn.getName()).thenReturn(
                "C=TW,ST=Taiwan,L=Taipei,O=Google,CN=" + issuer);

        when(mockCert.getSignature()).thenReturn(new byte[]{
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
                (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78,
                (byte) 0x90, (byte) 0xab, (byte) 0xcd, (byte) 0xef});

        when(mockCert.getBasicConstraints()).thenReturn(isCa ? 99 : -1);
        return mockCert;
    }

    private WifiConfiguration prepareWifiConfiguration(boolean isAtLeastT) {
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TLS, WifiEnterpriseConfig.Phase2.NONE));
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.SSID = TEST_SSID;
        if (isAtLeastT) {
            config.enterpriseConfig.enableTrustOnFirstUse(true);
        }
        config.enterpriseConfig.setCaPath("");
        config.enterpriseConfig.setDomainSuffixMatch("");
        return config;
    }

    private void setupTest(WifiConfiguration config,
            boolean isAtLeastT, boolean isTrustOnFirstUseSupported) {
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported, false);
    }

    private void setupTest(WifiConfiguration config,
            boolean isAtLeastT, boolean isTrustOnFirstUseSupported,
            boolean isInsecureEnterpriseConfigurationAllowed) {
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFrameworkFacade,
                mWifiNotificationManager,
                mWifiDialogManager,
                mClock, mTestAlarmManager.getAlarmManager(),
                isTrustOnFirstUseSupported,
                isInsecureEnterpriseConfigurationAllowed,
                mCallbacks,
                WIFI_IFACE_NAME,
                mHandler);

        mInsecureEapNetworkHandler.prepareConnection(config);

        if (isTrustOnFirstUseSupported && config.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            verify(mContext, atLeastOnce()).registerReceiver(
                    mBroadcastReceiverCaptor.capture(),
                    argThat(f -> f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP)),
                    eq(null),
                    eq(mHandler));
        } else if ((isTrustOnFirstUseSupported
                && !config.enterpriseConfig.isTrustOnFirstUseEnabled()
                && isInsecureEnterpriseConfigurationAllowed)
                || !isTrustOnFirstUseSupported) {
            verify(mContext, atLeastOnce()).registerReceiver(
                    mBroadcastReceiverCaptor.capture(),
                    argThat(f -> f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_ACCEPT)
                            && f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_REJECT)),
                    eq(null),
                    eq(mHandler));
        }
    }

    /**
     * Verify Trust On First Use flow with a minimal cert chain
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUserWithMinimalChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockCaCert = generateMockCert("ca", "ca", true);
        X509Certificate mockServerCert = generateMockCert("server", "ca", false);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 1, mockCaCert);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0, mockServerCert);

        verifyTrustOnFirstUseFlow(config, ACTION_ACCEPT, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockCaCert, mockServerCert);
    }

    /**
     * Verify Trust On First Use flow with a self-signed CA cert.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUserWithSelfSignedCaCert()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0, mockSelfSignedCert);

        verifyTrustOnFirstUseFlow(config, ACTION_ACCEPT, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockSelfSignedCert, mockSelfSignedCert);
    }

    /**
     * Verify that the connection should be terminated.
     * - TOFU is supported.
     * - Insecure EAP network is not allowed.
     * - No cert is received.
     */
    @Test
    public void verifyOnErrorWithoutCert() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        assertEquals(needUserApproval,
                mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that the connection should be terminated.
     * - TOFU is supported.
     * - Insecure EAP network is not allowed.
     * - TOFU is not enabled
     */
    @Test
    public void verifyOnErrorWithTofuDisabledWhenInsecureEapNetworkIsNotAllowed()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        config.enterpriseConfig.enableTrustOnFirstUse(false);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 1,
                generateMockCert("ca", "ca", true));
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        assertEquals(needUserApproval,
                mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that no error occurs in insecure network handling flow.
     * - TOFU is supported.
     * - Insecure EAP network is allowed.
     * - TOFU is not enabled
     * - No user approval is needed.
     */
    @Test
    public void verifyNoErrorWithTofuDisabledWhenInsecureEapNetworkIsAllowed()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = false, isInsecureEnterpriseConfigurationAllowed = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        config.enterpriseConfig.enableTrustOnFirstUse(false);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported,
                isInsecureEnterpriseConfigurationAllowed);

        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 1,
                generateMockCert("ca", "ca", true));
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        assertEquals(needUserApproval,
                mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks, never()).onError(any());
    }

    /**
     * Verify that it reports errors if the cert chain is headless.
     */
    @Test
    public void verifyOnErrorWithHeadlessCertChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        // Missing root CA cert.
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        assertTrue(mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that is reposrts errors if the server cert issuer does not match the parent subject.
     */
    @Test
    public void verifyOnErrorWithIncompleteChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockCaCert = generateMockCert("ca", "ca", true);
        // Missing intermediate cert.
        X509Certificate mockServerCert = generateMockCert("server", "intermediate", false);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 1, mockCaCert);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0, mockServerCert);

        assertTrue(mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that setting pending certificate won't crash with no current configuration.
     */
    @Test
    public void verifySetPendingCertificateNoCrashWithNoConfig()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFrameworkFacade,
                mWifiNotificationManager,
                mWifiDialogManager,
                mClock, mTestAlarmManager.getAlarmManager(),
                true /* isTrustOnFirstUseSupported */,
                false /* isInsecureEnterpriseConfigurationAllowed */,
                mCallbacks,
                WIFI_IFACE_NAME,
                mHandler);
        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.setPendingCertificate("NotExist", 0, mockSelfSignedCert);
    }

    @Test
    public void testExistingCertChainIsClearedOnPreparingNewConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        // Missing root CA cert.
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        // The wrong cert chain should be cleared after this call.
        mInsecureEapNetworkHandler.prepareConnection(config);

        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0, mockSelfSignedCert);

        assertTrue(mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks, never()).onError(any());
    }

    @Test
    public void verifyUserApprovalIsNotNeededWithDifferentTargetConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0, mockSelfSignedCert);

        // Pass another PSK config which is not the same as the current one.
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        pskConfig.networkId = FRAMEWORK_NETWORK_ID + 2;
        mInsecureEapNetworkHandler.prepareConnection(pskConfig);
        assertFalse(mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks, never()).onError(any());

        // Pass another non-TOFU EAP config which is not the same as the current one.
        WifiConfiguration anotherEapConfig = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        anotherEapConfig.networkId = FRAMEWORK_NETWORK_ID + 1;
        mInsecureEapNetworkHandler.prepareConnection(anotherEapConfig);
        assertFalse(mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));
        verify(mCallbacks, never()).onError(any());
    }

    @Test
    public void verifyDisconnectNetworkAfterNotificationWaitingTime() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockCaCert = generateMockCert("ca", "ca", true);
        X509Certificate mockServerCert = generateMockCert("server", "ca", false);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 1, mockCaCert);
        mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0, mockServerCert);

        assertEquals(needUserApproval,
                mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));

        verify(mTestAlarmManager.getAlarmManager()).set(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                eq(InsecureEapNetworkHandler.NOTIFICATION_WAITING_TIME_MS),
                eq(InsecureEapNetworkHandler.NOTIFICATION_WAITING_TIMER_TAG),
                any(), eq(mHandler));
        mTestAlarmManager.dispatch(InsecureEapNetworkHandler.NOTIFICATION_WAITING_TIMER_TAG);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
        verify(mWifiNative).disconnect(eq(WIFI_IFACE_NAME));
    }

    private void verifyTrustOnFirstUseFlowWithDefaultCerts(WifiConfiguration config,
            int action, boolean isTrustOnFirstUseSupported, boolean isUserSelected,
            boolean needUserApproval) throws Exception {
        X509Certificate mockCaCert = generateMockCert("ca", "ca", true);
        X509Certificate mockServerCert = generateMockCert("server", "middle", false);
        if (isTrustOnFirstUseSupported) {
            mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 2, mockCaCert);
            mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 1,
                    generateMockCert("middle", "ca", false));
            mInsecureEapNetworkHandler.setPendingCertificate(config.SSID, 0, mockServerCert);
        }
        verifyTrustOnFirstUseFlow(config, action, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockCaCert, mockServerCert);
    }

    private void verifyTrustOnFirstUseFlow(WifiConfiguration config,
            int action, boolean isTrustOnFirstUseSupported, boolean isUserSelected,
            boolean needUserApproval, X509Certificate expectedCaCert,
            X509Certificate expectedServerCert) throws Exception {
        assertEquals(needUserApproval,
                mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected));

        if (isUserSelected) {
            ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                    ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
            verify(mWifiDialogManager).createLegacySimpleDialogWithUrl(
                    any(), any(), any(), anyInt(), anyInt(), any(), any(), any(),
                    dialogCallbackCaptor.capture(), any());
            if (action == ACTION_ACCEPT) {
                dialogCallbackCaptor.getValue().onPositiveButtonClicked();
            } else if (action == ACTION_REJECT) {
                dialogCallbackCaptor.getValue().onNegativeButtonClicked();
            }
        } else {
            verify(mFrameworkFacade, never()).makeAlertDialogBuilder(any());
            verify(mFrameworkFacade).makeNotificationBuilder(
                    eq(mContext), eq(WifiService.NOTIFICATION_NETWORK_ALERTS));

            // Trust On First Use notification has no accept and reject action buttons.
            // It only supports TAP and launch the dialog.
            if (isTrustOnFirstUseSupported) {
                Intent intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP);
                intent.putExtra(InsecureEapNetworkHandler.EXTRA_PENDING_CERT_SSID, TEST_SSID);
                BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
                br.onReceive(mContext, intent);
                ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                        ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
                verify(mWifiDialogManager).createLegacySimpleDialogWithUrl(
                        any(), any(), any(), anyInt(), anyInt(), any(), any(), any(),
                        dialogCallbackCaptor.capture(), any());
                if (action == ACTION_ACCEPT) {
                    dialogCallbackCaptor.getValue().onPositiveButtonClicked();
                } else if (action == ACTION_REJECT) {
                    dialogCallbackCaptor.getValue().onNegativeButtonClicked();
                }
            } else {
                Intent intent = new Intent();
                if (action == ACTION_ACCEPT) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_ACCEPT);
                } else if (action == ACTION_REJECT) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_REJECT);
                } else if (action == ACTION_TAP) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP);
                }
                intent.putExtra(InsecureEapNetworkHandler.EXTRA_PENDING_CERT_SSID, TEST_SSID);
                BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
                br.onReceive(mContext, intent);
            }
        }

        if (action == ACTION_ACCEPT) {
            verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                    eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE));
            if (isTrustOnFirstUseSupported) {
                verify(mWifiConfigManager).updateCaCertificate(
                        eq(config.networkId), eq(expectedCaCert), eq(expectedServerCert));
            } else {
                verify(mWifiConfigManager, never()).updateCaCertificate(
                        anyInt(), any(), any());
            }
            verify(mCallbacks).onAccept(eq(config.SSID));
        } else if (action == ACTION_REJECT) {
            verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                    eq(WifiConfiguration.NetworkSelectionStatus
                            .DISABLED_BY_WIFI_MANAGER));
            verify(mCallbacks).onReject(eq(config.SSID));
        } else if (action == ACTION_TAP) {
            verify(mWifiDialogManager).createLegacySimpleDialogWithUrl(
                    any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any());
            verify(mTofuAlertDialog).launchDialog();
        }
        verify(mCallbacks, never()).onError(any());
    }

}
