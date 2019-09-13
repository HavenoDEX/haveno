/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.components.AutoTooltipRadioButton;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.trade.Contract;
import bisq.core.util.BSFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.add2ButtonsWithBox;
import static bisq.desktop.util.FormBuilder.addConfirmationLabelLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelWithVBox;

public class DisputeSummaryWindow extends Overlay<DisputeSummaryWindow> {
    private static final Logger log = LoggerFactory.getLogger(DisputeSummaryWindow.class);

    private final BSFormatter formatter;
    private final ArbitrationManager arbitrationManager;
    private final MediationManager mediationManager;
    private final BtcWalletService walletService;
    private final TradeWalletService tradeWalletService;
    private Dispute dispute;
    private Optional<Runnable> finalizeDisputeHandlerOptional = Optional.<Runnable>empty();
    private ToggleGroup tradeAmountToggleGroup, reasonToggleGroup;
    private DisputeResult disputeResult;
    private RadioButton buyerGetsTradeAmountRadioButton, sellerGetsTradeAmountRadioButton,
            buyerGetsAllRadioButton, sellerGetsAllRadioButton, customRadioButton;
    private RadioButton reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
            reasonProtocolViolationRadioButton, reasonNoReplyRadioButton, reasonWasScamRadioButton,
            reasonWasOtherRadioButton, reasonWasBankRadioButton;
    // Dispute object of other trade peer. The dispute field is the one from which we opened the close dispute window.
    private Optional<Dispute> peersDisputeOptional;
    private String role;
    private TextArea summaryNotesTextArea;

    private ChangeListener<Boolean> customRadioButtonSelectedListener;
    private ChangeListener<Toggle> reasonToggleSelectionListener;
    private InputTextField buyerPayoutAmountInputTextField, sellerPayoutAmountInputTextField;
    private ChangeListener<String> buyerPayoutAmountListener, sellerPayoutAmountListener;
    private CheckBox isLoserPublisherCheckBox;
    private ChangeListener<Toggle> tradeAmountToggleGroupListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DisputeSummaryWindow(BSFormatter formatter,
                                ArbitrationManager arbitrationManager,
                                MediationManager mediationManager,
                                BtcWalletService walletService,
                                TradeWalletService tradeWalletService) {

        this.formatter = formatter;
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;

        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 700;
        createGridPane();
        addContent();
        display();

        if (DevEnv.isDevMode()) {
            UserThread.execute(() -> {
                summaryNotesTextArea.setText("dummy result....");
            });
        }
    }

    public DisputeSummaryWindow onFinalizeDispute(Runnable finalizeDisputeHandler) {
        this.finalizeDisputeHandlerOptional = Optional.of(finalizeDisputeHandler);
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void cleanup() {
        if (reasonToggleGroup != null)
            reasonToggleGroup.selectedToggleProperty().removeListener(reasonToggleSelectionListener);

        if (customRadioButton != null)
            customRadioButton.selectedProperty().removeListener(customRadioButtonSelectedListener);

        if (tradeAmountToggleGroup != null)
            tradeAmountToggleGroup.selectedToggleProperty().removeListener(tradeAmountToggleGroupListener);

        removePayoutAmountListeners();
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
        gridPane.getColumnConstraints().get(0).setHalignment(HPos.LEFT);
        gridPane.setPrefWidth(900);
    }

    private void addContent() {
        Contract contract = dispute.getContract();
        if (dispute.getDisputeResultProperty().get() == null)
            disputeResult = new DisputeResult(dispute.getTradeId(), dispute.getTraderId());
        else
            disputeResult = dispute.getDisputeResultProperty().get();

        peersDisputeOptional = getDisputeManager(dispute).getDisputesAsObservableList().stream()
                .filter(d -> dispute.getTradeId().equals(d.getTradeId()) && dispute.getTraderId() != d.getTraderId())
                .findFirst();

        addInfoPane();

        addTradeAmountPayoutControls();
        addPayoutAmountTextFields();
        addReasonControls();

        boolean applyPeersDisputeResult = peersDisputeOptional.isPresent() && peersDisputeOptional.get().isClosed();
        if (applyPeersDisputeResult) {
            // If the other peers dispute has been closed we apply the result to ourselves
            DisputeResult peersDisputeResult = peersDisputeOptional.get().getDisputeResultProperty().get();
            disputeResult.setBuyerPayoutAmount(peersDisputeResult.getBuyerPayoutAmount());
            disputeResult.setSellerPayoutAmount(peersDisputeResult.getSellerPayoutAmount());
            disputeResult.setWinner(peersDisputeResult.getWinner());
            disputeResult.setLoserPublisher(peersDisputeResult.isLoserPublisher());
            disputeResult.setReason(peersDisputeResult.getReason());
            disputeResult.setSummaryNotes(peersDisputeResult.summaryNotesProperty().get());

            buyerGetsTradeAmountRadioButton.setDisable(true);
            buyerGetsAllRadioButton.setDisable(true);
            sellerGetsTradeAmountRadioButton.setDisable(true);
            sellerGetsAllRadioButton.setDisable(true);
            customRadioButton.setDisable(true);

            buyerPayoutAmountInputTextField.setDisable(true);
            sellerPayoutAmountInputTextField.setDisable(true);
            buyerPayoutAmountInputTextField.setEditable(false);
            sellerPayoutAmountInputTextField.setEditable(false);

            reasonWasBugRadioButton.setDisable(true);
            reasonWasUsabilityIssueRadioButton.setDisable(true);
            reasonProtocolViolationRadioButton.setDisable(true);
            reasonNoReplyRadioButton.setDisable(true);
            reasonWasScamRadioButton.setDisable(true);
            reasonWasOtherRadioButton.setDisable(true);
            reasonWasBankRadioButton.setDisable(true);

            isLoserPublisherCheckBox.setDisable(true);
            isLoserPublisherCheckBox.setSelected(peersDisputeResult.isLoserPublisher());

            applyPayoutAmounts(tradeAmountToggleGroup.selectedToggleProperty().get());
            applyTradeAmountRadioButtonStates();
        } else {
            isLoserPublisherCheckBox.setSelected(false);
        }

        setReasonRadioButtonState();

        addSummaryNotes();
        addButtons(contract);
    }

    private void addInfoPane() {
        Contract contract = dispute.getContract();
        addTitledGroupBg(gridPane, ++rowIndex, 17, Res.get("disputeSummaryWindow.title")).getStyleClass().add("last");
        addConfirmationLabelLabel(gridPane, rowIndex, Res.get("shared.tradeId"), dispute.getShortTradeId(),
                Layout.TWICE_FIRST_ROW_DISTANCE);
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.openDate"), formatter.formatDateTime(dispute.getOpeningDate()));
        if (dispute.isDisputeOpenerIsMaker()) {
            if (dispute.isDisputeOpenerIsBuyer())
                role = Res.get("support.buyerOfferer");
            else
                role = Res.get("support.sellerOfferer");
        } else {
            if (dispute.isDisputeOpenerIsBuyer())
                role = Res.get("support.buyerTaker");
            else
                role = Res.get("support.sellerTaker");
        }
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.role"), role);
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradeAmount"),
                formatter.formatCoinWithCode(contract.getTradeAmount()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradePrice"),
                formatter.formatPrice(contract.getTradePrice()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradeVolume"),
                formatter.formatVolumeWithCode(contract.getTradeVolume()));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(contract.getOfferPayload().getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(contract.getOfferPayload().getSellerSecurityDeposit());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.securityDeposit"), securityDeposit);
    }

    private void addTradeAmountPayoutControls() {
        buyerGetsTradeAmountRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount",
                Res.get("shared.buyer")));
        buyerGetsAllRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsAll",
                Res.get("shared.buyer")));
        sellerGetsTradeAmountRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount",
                Res.get("shared.seller")));
        sellerGetsAllRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsAll",
                Res.get("shared.seller")));
        customRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.custom"));

        VBox radioButtonPane = new VBox();
        radioButtonPane.setSpacing(10);
        radioButtonPane.getChildren().addAll(buyerGetsTradeAmountRadioButton, buyerGetsAllRadioButton,
                sellerGetsTradeAmountRadioButton, sellerGetsAllRadioButton,
                customRadioButton);

        addTopLabelWithVBox(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.payout"), radioButtonPane, 0);

        tradeAmountToggleGroup = new ToggleGroup();
        buyerGetsTradeAmountRadioButton.setToggleGroup(tradeAmountToggleGroup);
        buyerGetsAllRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerGetsTradeAmountRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerGetsAllRadioButton.setToggleGroup(tradeAmountToggleGroup);
        customRadioButton.setToggleGroup(tradeAmountToggleGroup);

        tradeAmountToggleGroupListener = (observable, oldValue, newValue) -> applyPayoutAmounts(newValue);
        tradeAmountToggleGroup.selectedToggleProperty().addListener(tradeAmountToggleGroupListener);

        buyerPayoutAmountListener = (observable1, oldValue1, newValue1) -> applyCustomAmounts(buyerPayoutAmountInputTextField);
        sellerPayoutAmountListener = (observable1, oldValue1, newValue1) -> applyCustomAmounts(sellerPayoutAmountInputTextField);

        customRadioButtonSelectedListener = (observable, oldValue, newValue) -> {
            buyerPayoutAmountInputTextField.setEditable(newValue);
            sellerPayoutAmountInputTextField.setEditable(newValue);

            if (newValue) {
                buyerPayoutAmountInputTextField.textProperty().addListener(buyerPayoutAmountListener);
                sellerPayoutAmountInputTextField.textProperty().addListener(sellerPayoutAmountListener);
            } else {
                removePayoutAmountListeners();
            }
        };
        customRadioButton.selectedProperty().addListener(customRadioButtonSelectedListener);
    }

    private void removePayoutAmountListeners() {
        if (buyerPayoutAmountInputTextField != null && buyerPayoutAmountListener != null)
            buyerPayoutAmountInputTextField.textProperty().removeListener(buyerPayoutAmountListener);

        if (sellerPayoutAmountInputTextField != null && sellerPayoutAmountListener != null)
            sellerPayoutAmountInputTextField.textProperty().removeListener(sellerPayoutAmountListener);

    }

    private boolean isPayoutAmountValid() {
        Coin buyerAmount = formatter.parseToCoin(buyerPayoutAmountInputTextField.getText());
        Coin sellerAmount = formatter.parseToCoin(sellerPayoutAmountInputTextField.getText());
        Contract contract = dispute.getContract();
        Coin tradeAmount = contract.getTradeAmount();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin available = tradeAmount
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
        Coin totalAmount = buyerAmount.add(sellerAmount);
        return (totalAmount.compareTo(available) == 0);
    }

    private void applyCustomAmounts(InputTextField inputTextField) {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin available = contract.getTradeAmount()
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
        Coin enteredAmount = formatter.parseToCoin(inputTextField.getText());
        if (enteredAmount.compareTo(available) > 0) {
            enteredAmount = available;
            Coin finalEnteredAmount = enteredAmount;
            inputTextField.setText(formatter.formatCoin(finalEnteredAmount));
        }
        Coin counterPartAsCoin = available.subtract(enteredAmount);
        String formattedCounterPartAmount = formatter.formatCoin(counterPartAsCoin);
        Coin buyerAmount;
        Coin sellerAmount;
        if (inputTextField == buyerPayoutAmountInputTextField) {
            buyerAmount = enteredAmount;
            sellerAmount = counterPartAsCoin;
            sellerPayoutAmountInputTextField.setText(formattedCounterPartAmount);
        } else {
            sellerAmount = enteredAmount;
            buyerAmount = counterPartAsCoin;
            buyerPayoutAmountInputTextField.setText(formattedCounterPartAmount);
        }

        disputeResult.setBuyerPayoutAmount(buyerAmount);
        disputeResult.setSellerPayoutAmount(sellerAmount);
        disputeResult.setWinner(buyerAmount.compareTo(sellerAmount) > 0 ?
                DisputeResult.Winner.BUYER :
                DisputeResult.Winner.SELLER);
    }

    private void addPayoutAmountTextFields() {
        buyerPayoutAmountInputTextField = new InputTextField();
        buyerPayoutAmountInputTextField.setLabelFloat(true);
        buyerPayoutAmountInputTextField.setEditable(false);
        buyerPayoutAmountInputTextField.setPromptText(Res.get("disputeSummaryWindow.payoutAmount.buyer"));

        sellerPayoutAmountInputTextField = new InputTextField();
        sellerPayoutAmountInputTextField.setLabelFloat(true);
        sellerPayoutAmountInputTextField.setPromptText(Res.get("disputeSummaryWindow.payoutAmount.seller"));
        sellerPayoutAmountInputTextField.setEditable(false);

        isLoserPublisherCheckBox = new AutoTooltipCheckBox(Res.get("disputeSummaryWindow.payoutAmount.invert"));

        VBox vBox = new VBox();
        vBox.setSpacing(15);
        vBox.getChildren().addAll(buyerPayoutAmountInputTextField, sellerPayoutAmountInputTextField, isLoserPublisherCheckBox);
        GridPane.setMargin(vBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        GridPane.setRowIndex(vBox, rowIndex);
        GridPane.setColumnIndex(vBox, 1);
        gridPane.getChildren().add(vBox);
    }

    private void addReasonControls() {
        reasonWasBugRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.bug"));
        reasonWasUsabilityIssueRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.usability"));
        reasonProtocolViolationRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.protocolViolation"));
        reasonNoReplyRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.noReply"));
        reasonWasScamRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.scam"));
        reasonWasBankRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.bank"));
        reasonWasOtherRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.other"));

        HBox feeRadioButtonPane = new HBox();
        feeRadioButtonPane.setSpacing(20);
        feeRadioButtonPane.getChildren().addAll(reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
                reasonProtocolViolationRadioButton, reasonNoReplyRadioButton,
                reasonWasBankRadioButton, reasonWasScamRadioButton, reasonWasOtherRadioButton);

        VBox vBox = addTopLabelWithVBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.reason"),
                feeRadioButtonPane, 10).second;
        GridPane.setColumnSpan(vBox, 2);

        reasonToggleGroup = new ToggleGroup();
        reasonWasBugRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasUsabilityIssueRadioButton.setToggleGroup(reasonToggleGroup);
        reasonProtocolViolationRadioButton.setToggleGroup(reasonToggleGroup);
        reasonNoReplyRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasScamRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasOtherRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasBankRadioButton.setToggleGroup(reasonToggleGroup);

        reasonToggleSelectionListener = (observable, oldValue, newValue) -> {
            if (newValue == reasonWasBugRadioButton)
                disputeResult.setReason(DisputeResult.Reason.BUG);
            else if (newValue == reasonWasUsabilityIssueRadioButton)
                disputeResult.setReason(DisputeResult.Reason.USABILITY);
            else if (newValue == reasonProtocolViolationRadioButton)
                disputeResult.setReason(DisputeResult.Reason.PROTOCOL_VIOLATION);
            else if (newValue == reasonNoReplyRadioButton)
                disputeResult.setReason(DisputeResult.Reason.NO_REPLY);
            else if (newValue == reasonWasScamRadioButton)
                disputeResult.setReason(DisputeResult.Reason.SCAM);
            else if (newValue == reasonWasBankRadioButton)
                disputeResult.setReason(DisputeResult.Reason.BANK_PROBLEMS);
            else if (newValue == reasonWasOtherRadioButton)
                disputeResult.setReason(DisputeResult.Reason.OTHER);
        };
        reasonToggleGroup.selectedToggleProperty().addListener(reasonToggleSelectionListener);
    }

    private void setReasonRadioButtonState() {
        if (disputeResult.getReason() != null) {
            switch (disputeResult.getReason()) {
                case BUG:
                    reasonToggleGroup.selectToggle(reasonWasBugRadioButton);
                    break;
                case USABILITY:
                    reasonToggleGroup.selectToggle(reasonWasUsabilityIssueRadioButton);
                    break;
                case PROTOCOL_VIOLATION:
                    reasonToggleGroup.selectToggle(reasonProtocolViolationRadioButton);
                    break;
                case NO_REPLY:
                    reasonToggleGroup.selectToggle(reasonNoReplyRadioButton);
                    break;
                case SCAM:
                    reasonToggleGroup.selectToggle(reasonWasScamRadioButton);
                    break;
                case BANK_PROBLEMS:
                    reasonToggleGroup.selectToggle(reasonWasBankRadioButton);
                    break;
                case OTHER:
                    reasonToggleGroup.selectToggle(reasonWasOtherRadioButton);
                    break;
            }
        }
    }

    private void addSummaryNotes() {
        summaryNotesTextArea = new BisqTextArea();
        summaryNotesTextArea.setPromptText(Res.get("disputeSummaryWindow.addSummaryNotes"));
        summaryNotesTextArea.setWrapText(true);

        Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.summaryNotes"), summaryNotesTextArea, 0);
        GridPane.setColumnSpan(topLabelWithVBox.second, 2);

        summaryNotesTextArea.setPrefHeight(50);
        summaryNotesTextArea.textProperty().bindBidirectional(disputeResult.summaryNotesProperty());
    }

    private void addButtons(Contract contract) {
        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.close.button"),
                Res.get("shared.cancel"), 15, true);
        //GridPane.setColumnSpan(tuple.third, 2);
        Button closeTicketButton = tuple.first;
        closeTicketButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> tradeAmountToggleGroup.getSelectedToggle() == null
                        || summaryNotesTextArea.getText() == null
                        || summaryNotesTextArea.getText().length() == 0
                        || !isPayoutAmountValid(),
                tradeAmountToggleGroup.selectedToggleProperty(),
                summaryNotesTextArea.textProperty(),
                buyerPayoutAmountInputTextField.textProperty(),
                sellerPayoutAmountInputTextField.textProperty()));

        Button cancelButton = tuple.second;

        closeTicketButton.setOnAction(e -> {
            if (dispute.getDepositTxSerialized() == null) {
                log.warn("dispute.getDepositTxSerialized is null");
                return;
            }

            if (!dispute.isMediationDispute()) {
                try {
                    AddressEntry arbitratorAddressEntry = walletService.getArbitratorAddressEntry();
                    disputeResult.setArbitratorPubKey(arbitratorAddressEntry.getPubKey());
                    byte[] arbitratorSignature = tradeWalletService.arbitratorSignsDisputedPayoutTx(
                            dispute.getDepositTxSerialized(),
                            disputeResult.getBuyerPayoutAmount(),
                            disputeResult.getSellerPayoutAmount(),
                            contract.getBuyerPayoutAddressString(),
                            contract.getSellerPayoutAddressString(),
                            arbitratorAddressEntry.getKeyPair(),
                            contract.getBuyerMultiSigPubKey(),
                            contract.getSellerMultiSigPubKey(),
                            arbitratorAddressEntry.getPubKey()
                    );
                    disputeResult.setArbitratorSignature(arbitratorSignature);
                } catch (AddressFormatException | TransactionVerificationException e2) {
                    log.error("Error at close dispute", e2);
                    return;
                }
            }

            disputeResult.setLoserPublisher(isLoserPublisherCheckBox.isSelected());
            disputeResult.setCloseDate(new Date());
            dispute.setDisputeResult(disputeResult);
            dispute.setIsClosed(true);
            String text = Res.get("disputeSummaryWindow.close.msg",
                    formatter.formatDateTime(disputeResult.getCloseDate()),
                    formatter.formatCoinWithCode(disputeResult.getBuyerPayoutAmount()),
                    formatter.formatCoinWithCode(disputeResult.getSellerPayoutAmount()),
                    disputeResult.summaryNotesProperty().get());

            if (dispute.isMediationDispute()) {
                text += Res.get("disputeSummaryWindow.close.nextStepsForMediation");
            }

            getDisputeManager(dispute).sendDisputeResultMessage(disputeResult, dispute, text);

            if (peersDisputeOptional.isPresent() && !peersDisputeOptional.get().isClosed() && !DevEnv.isDevMode()) {
                UserThread.runAfter(() -> new Popup<>()
                                .attention(Res.get("disputeSummaryWindow.close.closePeer"))
                                .show(),
                        200, TimeUnit.MILLISECONDS);
            }

            finalizeDisputeHandlerOptional.ifPresent(Runnable::run);

            closeTicketButton.disableProperty().unbind();

            hide();
        });

        cancelButton.setOnAction(e -> {
            dispute.setDisputeResult(disputeResult);
            hide();
        });
    }

    private DisputeManager<? extends DisputeList<? extends DisputeList>> getDisputeManager(Dispute dispute) {
        return dispute.isMediationDispute() ? mediationManager : arbitrationManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Controller
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyPayoutAmounts(Toggle selectedTradeAmountToggle) {
        if (selectedTradeAmountToggle != customRadioButton && selectedTradeAmountToggle != null) {
            applyPayoutAmountsToDisputeResult(selectedTradeAmountToggle);
            applyTradeAmountRadioButtonStates();
        }
    }

    private void applyPayoutAmountsToDisputeResult(Toggle selectedTradeAmountToggle) {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin buyerSecurityDeposit = offer.getBuyerSecurityDeposit();
        Coin sellerSecurityDeposit = offer.getSellerSecurityDeposit();
        Coin tradeAmount = contract.getTradeAmount();
        if (selectedTradeAmountToggle == buyerGetsTradeAmountRadioButton) {
            disputeResult.setBuyerPayoutAmount(tradeAmount.add(buyerSecurityDeposit));
            disputeResult.setSellerPayoutAmount(sellerSecurityDeposit);
            disputeResult.setWinner(DisputeResult.Winner.BUYER);
        } else if (selectedTradeAmountToggle == buyerGetsAllRadioButton) {
            disputeResult.setBuyerPayoutAmount(tradeAmount
                    .add(buyerSecurityDeposit)
                    .add(sellerSecurityDeposit));
            disputeResult.setSellerPayoutAmount(Coin.ZERO);
            disputeResult.setWinner(DisputeResult.Winner.BUYER);
        } else if (selectedTradeAmountToggle == sellerGetsTradeAmountRadioButton) {
            disputeResult.setBuyerPayoutAmount(buyerSecurityDeposit);
            disputeResult.setSellerPayoutAmount(tradeAmount.add(sellerSecurityDeposit));
            disputeResult.setWinner(DisputeResult.Winner.SELLER);
        } else if (selectedTradeAmountToggle == sellerGetsAllRadioButton) {
            disputeResult.setBuyerPayoutAmount(Coin.ZERO);
            disputeResult.setSellerPayoutAmount(tradeAmount
                    .add(sellerSecurityDeposit)
                    .add(buyerSecurityDeposit));
            disputeResult.setWinner(DisputeResult.Winner.SELLER);
        }

        buyerPayoutAmountInputTextField.setText(formatter.formatCoin(disputeResult.getBuyerPayoutAmount()));
        sellerPayoutAmountInputTextField.setText(formatter.formatCoin(disputeResult.getSellerPayoutAmount()));
    }

    private void applyTradeAmountRadioButtonStates() {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin buyerSecurityDeposit = offer.getBuyerSecurityDeposit();
        Coin sellerSecurityDeposit = offer.getSellerSecurityDeposit();
        Coin tradeAmount = contract.getTradeAmount();

        Coin buyerPayoutAmount = disputeResult.getBuyerPayoutAmount();
        Coin sellerPayoutAmount = disputeResult.getSellerPayoutAmount();

        buyerPayoutAmountInputTextField.setText(formatter.formatCoin(buyerPayoutAmount));
        sellerPayoutAmountInputTextField.setText(formatter.formatCoin(sellerPayoutAmount));

        if (buyerPayoutAmount.equals(tradeAmount.add(buyerSecurityDeposit)) &&
                sellerPayoutAmount.equals(sellerSecurityDeposit)) {
            buyerGetsTradeAmountRadioButton.setSelected(true);
        } else if (buyerPayoutAmount.equals(tradeAmount.add(buyerSecurityDeposit).add(sellerSecurityDeposit)) &&
                sellerPayoutAmount.equals(Coin.ZERO)) {
            buyerGetsAllRadioButton.setSelected(true);
        } else if (sellerPayoutAmount.equals(tradeAmount.add(sellerSecurityDeposit))
                && buyerPayoutAmount.equals(buyerSecurityDeposit)) {
            sellerGetsTradeAmountRadioButton.setSelected(true);
        } else if (sellerPayoutAmount.equals(tradeAmount.add(buyerSecurityDeposit).add(sellerSecurityDeposit))
                && buyerPayoutAmount.equals(Coin.ZERO)) {
            sellerGetsAllRadioButton.setSelected(true);
        } else {
            customRadioButton.setSelected(true);
        }
    }
}
