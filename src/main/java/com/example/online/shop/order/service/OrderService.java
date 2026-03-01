package com.example.online.shop.order.service;

import com.example.online.shop.payment.service.PaymentService;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
   PaymentService paymentService = new PaymentService();
}
