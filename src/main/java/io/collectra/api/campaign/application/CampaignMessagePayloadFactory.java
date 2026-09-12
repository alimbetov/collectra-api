package io.collectra.api.campaign.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.receivable.domain.Invoice;
import org.springframework.stereotype.Component;

@Component
public class CampaignMessagePayloadFactory {
    private final ObjectMapper json;

    public CampaignMessagePayloadFactory(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode create(Customer customer, Invoice invoice) {
        ObjectNode root = json.createObjectNode();
        ObjectNode document = root.putObject("document");
        ObjectNode customerNode = root.putObject("customer");
        ObjectNode invoiceNode = root.putObject("invoice");
        ObjectNode custom = root.putObject("custom");

        put(customerNode, "externalId", customer.getExternalId());
        put(customerNode, "type", customer.getCustomerType().name());
        put(customerNode, "displayName", customer.getDisplayName());
        put(customerNode, "name", customer.getDisplayName());
        put(customerNode, "firstName", customer.getFirstName());
        put(customerNode, "lastName", customer.getLastName());
        put(customerNode, "middleName", customer.getMiddleName());
        put(customerNode, "companyName", customer.getCompanyName());
        put(customerNode, "locale", customer.getPreferredLocale());
        put(customerNode, "timezone", customer.getTimezone());
        custom.set("customer", objectOrEmpty(customer.getCustomFields()));

        if (invoice == null) {
            custom.set("invoice", json.createObjectNode());
            return root;
        }

        put(document, "number", invoice.getInvoiceNumber());
        put(
                document,
                "date",
                invoice.getInvoiceDate() == null ? null : invoice.getInvoiceDate().toString());

        put(invoiceNode, "externalId", invoice.getExternalId());
        put(invoiceNode, "invoiceNumber", invoice.getInvoiceNumber());
        put(
                invoiceNode,
                "invoiceDate",
                invoice.getInvoiceDate() == null ? null : invoice.getInvoiceDate().toString());
        put(invoiceNode, "dueDate", invoice.getDueDate().toString());
        invoiceNode.put("amount", invoice.getOriginalAmount());
        invoiceNode.put("originalAmount", invoice.getOriginalAmount());
        invoiceNode.put("paidAmount", invoice.getPaidAmount());
        invoiceNode.put("outstandingAmount", invoice.getOutstandingAmount());
        put(invoiceNode, "currency", invoice.getCurrency());
        put(invoiceNode, "paymentStatus", invoice.getPaymentStatus().name());
        put(
                invoiceNode,
                "contractId",
                invoice.getContractId() == null ? null : invoice.getContractId().toString());
        put(
                invoiceNode,
                "documentFileId",
                invoice.getDocumentFileId() == null ? null : invoice.getDocumentFileId().toString());
        custom.set("invoice", objectOrEmpty(invoice.getCustomFields()));
        return root;
    }

    private JsonNode objectOrEmpty(JsonNode value) {
        return value != null && value.isObject() ? value.deepCopy() : json.createObjectNode();
    }

    private static void put(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }
}
